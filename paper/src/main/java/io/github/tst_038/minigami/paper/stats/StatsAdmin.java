package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

/**
 * Changes the stored totals by hand: a stat of one player and game raised, lowered or set, and the totals of a player
 * or a game cleared. The matches themselves stay in the history; only the running totals, which are what the stats and boards read, change.
 * A hand edit lands on the game's own row, not on one of its variants, so it shows in the game's totals and board but not in a variant's.
 *
 * <p>Call from the server thread.
 */
public final class StatsAdmin {

    private final Storage storage;
    private final PlayerProfiles profiles;
    private final Leaderboards leaderboards;
    private final StatsView view;
    private final Executor serverThread;
    private final Messages messages;
    private final Logger logger;

    public StatsAdmin(Storage storage, PlayerProfiles profiles, Leaderboards leaderboards, StatsView view, Executor serverThread, Messages messages,
            Logger logger) {
        this.storage = storage;
        this.profiles = profiles;
        this.leaderboards = leaderboards;
        this.view = view;
        this.serverThread = serverThread;
        this.messages = messages;
        this.logger = logger;
    }

    /** Raises the stat by the amount; a negative amount lowers it. */
    public void add(CommandSender sender, String playerName, String game, String stat, long amount) {
        change(sender, playerName, game, stat, "add", (connection, id) -> {
            adjust(connection, id, game, stat, amount);
            return current(connection, id, game, stat);
        });
    }

    /** Makes the stat exactly this, by changing the game's own row by what is missing. */
    public void set(CommandSender sender, String playerName, String game, String stat, long amount) {
        change(sender, playerName, game, stat, "set", (connection, id) -> {
            adjust(connection, id, game, stat, amount - current(connection, id, game, stat));
            return amount;
        });
    }

    /** Clears one game's totals of the player, or every game's. */
    public void resetPlayer(CommandSender sender, String playerName, Optional<String> game) {
        if (!knownGame(sender, game)) {
            return;
        }
        profiles.find(playerName).whenComplete((found, failure) -> {
            if (failure != null || found.isEmpty()) {
                messages.send(sender, "stats.unknown-player", Placeholder.unparsed("player", playerName));
                return;
            }
            var id = found.get().id();
            run(sender, connection -> {
                delete(connection, "player_totals", "player = ?", id.toString(), game);
                delete(connection, "player_stat_totals", "player = ?", id.toString(), game);
                return null;
            }, () -> {
                profiles.reload(java.util.Set.of(id));
                messages.send(sender, "stats.admin.reset-player", Placeholder.unparsed("player", found.get().name()),
                        Placeholder.unparsed("game", game.orElse("*")));
            });
        });
    }

    /** Clears the totals of everyone in the game, or in one of its variants. */
    public void resetGame(CommandSender sender, String game, Optional<String> variant) {
        if (!knownGame(sender, Optional.of(game))) {
            return;
        }
        run(sender, connection -> {
            for (var table : new String[] {"player_totals", "player_stat_totals"}) {
                try (var delete = connection.prepareStatement("DELETE FROM " + table + " WHERE game = ?" + (variant.isPresent() ? " AND variant = ?" : ""))) {
                    delete.setString(1, game);
                    if (variant.isPresent()) {
                        delete.setString(2, variant.get());
                    }
                    delete.executeUpdate();
                }
            }
            return null;
        }, () -> {
            profiles.reloadOnline();
            leaderboards.refresh();
            messages.send(sender, "stats.admin.reset-game", Placeholder.unparsed("game", game + variant.map(name -> " (" + name + ")").orElse("")));
        });
    }

    /**
     * Moves everything stored under one game id to another, the match history included, and reloads what is cached.
     * The id must not have stats of its own yet.
     */
    public CompletableFuture<Void> renameGame(String from, String to) {
        return storage.submit(connection -> {
            for (var table : new String[] {"matches", "player_totals", "player_stat_totals"}) {
                try (var update = connection.prepareStatement("UPDATE " + table + " SET game = ? WHERE game = ?")) {
                    update.setString(1, to);
                    update.setString(2, from);
                    update.executeUpdate();
                }
            }
            return null;
        }).thenRunAsync(() -> {
            profiles.reloadOnline();
            leaderboards.refresh();
        }, serverThread);
    }

    private interface Edit {
        long apply(Connection connection, UUID id) throws SQLException;
    }

    private void change(CommandSender sender, String playerName, String game, String stat, String kind, Edit edit) {
        if (!knownGame(sender, Optional.of(game))) {
            return;
        }
        if (!view.boardStats(game).contains(stat)) {
            messages.send(sender, "stats.unknown-stat", Placeholder.parsed("game", game), Placeholder.unparsed("stat", stat));
            return;
        }
        profiles.find(playerName).whenComplete((found, failure) -> {
            if (failure != null || found.isEmpty()) {
                messages.send(sender, "stats.unknown-player", Placeholder.unparsed("player", playerName));
                return;
            }
            var id = found.get().id();
            var result = new long[1];
            run(sender, connection -> {
                result[0] = edit.apply(connection, id);
                return null;
            }, () -> {
                profiles.reload(java.util.Set.of(id));
                leaderboards.refresh();
                messages.send(sender, "stats.admin.changed", Placeholder.unparsed("player", found.get().name()), Placeholder.unparsed("game", game),
                        Placeholder.unparsed("stat", stat), Formatter.number("amount", result[0]));
            });
        });
    }

    private <T> void run(CommandSender sender, Storage.Work<T> work, Runnable done) {
        storage.submit(work).whenCompleteAsync((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not change the stats", failure);
                messages.send(sender, "stats.failed");
            } else {
                done.run();
            }
        }, serverThread);
    }

    private boolean knownGame(CommandSender sender, Optional<String> game) {
        if (game.isPresent() && !view.gameIds().contains(game.get())) {
            messages.send(sender, "stats.unknown-game", Placeholder.unparsed("game", game.get()));
            return false;
        }
        return true;
    }

    private static boolean isTotal(String stat) {
        return stat.equals(Leaderboards.WINS) || stat.equals(Leaderboards.PLAYED);
    }

    /** The stat summed over the game's variants, as the stats and boards show it. */
    private static long current(Connection connection, UUID id, String game, String stat) throws SQLException {
        var sql = isTotal(stat) ? "SELECT COALESCE(SUM(" + stat + "), 0) FROM player_totals WHERE player = ? AND game = ?"
                : "SELECT COALESCE(SUM(amount), 0) FROM player_stat_totals WHERE player = ? AND game = ? AND stat = ?";
        try (var select = connection.prepareStatement(sql)) {
            select.setString(1, id.toString());
            select.setString(2, game);
            if (!isTotal(stat)) {
                select.setString(3, stat);
            }
            try (var rows = select.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void adjust(Connection connection, UUID id, String game, String stat, long delta) throws SQLException {
        var update = isTotal(stat) ? "UPDATE player_totals SET " + stat + " = " + stat + " + ? WHERE player = ? AND game = ? AND variant = ''"
                : "UPDATE player_stat_totals SET amount = amount + ? WHERE player = ? AND game = ? AND variant = '' AND stat = ?";
        try (var statement = connection.prepareStatement(update)) {
            statement.setLong(1, delta);
            statement.setString(2, id.toString());
            statement.setString(3, game);
            if (!isTotal(stat)) {
                statement.setString(4, stat);
            }
            if (statement.executeUpdate() > 0) {
                return;
            }
        }
        var insert = isTotal(stat)
                ? "INSERT INTO player_totals (player, game, variant, played, wins, placed, place_sum, left_matches, seconds_played) VALUES (?, ?, '', "
                        + (stat.equals(Leaderboards.PLAYED) ? "?, 0" : "0, ?") + ", 0, 0, 0, 0)"
                : "INSERT INTO player_stat_totals (player, game, variant, stat, amount) VALUES (?, ?, '', ?, ?)";
        try (var statement = connection.prepareStatement(insert)) {
            statement.setString(1, id.toString());
            statement.setString(2, game);
            if (isTotal(stat)) {
                statement.setLong(3, delta);
            } else {
                statement.setString(3, stat);
                statement.setLong(4, delta);
            }
            statement.executeUpdate();
        }
    }

    private static void delete(Connection connection, String table, String where, String player, Optional<String> game) throws SQLException {
        try (var delete = connection.prepareStatement("DELETE FROM " + table + " WHERE " + where + (game.isPresent() ? " AND game = ?" : ""))) {
            delete.setString(1, player);
            if (game.isPresent()) {
                delete.setString(2, game.get());
            }
            delete.executeUpdate();
        }
    }
}
