package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.game.Game;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shows a player's stats, per game type or for one game, and the leaderboards. A player online comes
 * from the profile cache; anyone else is looked up by name in the background. A stat's display
 * name is the message {@code stats.name.<stat>}.
 *
 * <p>Call the public methods from the server thread.
 */
public final class StatsView {

    private final PlayerProfiles profiles;
    private final Leaderboards leaderboards;
    private final Supplier<IdRegistry<Game>> games;
    private final Server server;
    private final Messages messages;
    private final Logger logger;

    public StatsView(PlayerProfiles profiles, Leaderboards leaderboards, Supplier<IdRegistry<Game>> games, Server server, Messages messages, Logger logger) {
        this.profiles = profiles;
        this.leaderboards = leaderboards;
        this.games = games;
        this.server = server;
        this.messages = messages;
        this.logger = logger;
    }

    public List<String> onlineNames() {
        return server.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    public SortedSet<String> gameIds() {
        return games.get().ids();
    }

    /** The stats the game has a board for, plus those it keeps, so a new game can be looked at before anyone played it. */
    public Set<String> boardStats(String gameId) {
        var stats = new TreeSet<String>();
        games.get().find(gameId).ifPresent(game -> {
            stats.add(Leaderboards.WINS);
            stats.addAll(statIds(game));
            stats.addAll(leaderboards.stats(gametypeId(game), game.id()));
        });
        return stats;
    }

    /** What every variant keeps, since they are counted together under the game. */
    private static Set<String> statIds(Game game) {
        var stats = new TreeSet<String>();
        game.all().forEach(variant -> variant.stats().forEach(stat -> stats.add(stat.id())));
        return stats;
    }

    private static String gametypeId(Game game) {
        return game.base().gametype().id();
    }

    /** @param gameId empty for the totals of every game they played */
    public void show(CommandSender sender, Player target, Optional<String> gameId) {
        profiles.of(target.getUniqueId()).ifPresentOrElse(
                profile -> show(sender, target.getName(), profile, gameId),
                () -> messages.send(sender, "stats.loading", Placeholder.unparsed("player", target.getName())));
    }

    /** @param name of a player online or not, in any case */
    public void show(CommandSender sender, String name, Optional<String> gameId) {
        if (gameId.isPresent() && findGame(sender, gameId.get()).isEmpty()) {
            return;
        }
        var online = server.getPlayerExact(name);
        if (online != null && profiles.of(online.getUniqueId()).isPresent()) {
            show(sender, online, gameId);
            return;
        }
        profiles.find(name).whenComplete((found, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not look up the stats of " + name, failure);
                messages.send(sender, "stats.failed");
            } else if (found.isEmpty()) {
                messages.send(sender, "stats.unknown-player", Placeholder.unparsed("player", name));
            } else {
                show(sender, found.get().name(), found.get().profile(), gameId);
            }
        });
    }

    public void top(CommandSender sender, String gameId, String stat) {
        var game = findGame(sender, gameId);
        if (game.isEmpty()) {
            return;
        }
        var gameName = Placeholder.parsed("game", game.get().name());
        if (!boardStats(gameId).contains(stat)) {
            messages.send(sender, "stats.unknown-stat", gameName, Placeholder.unparsed("stat", stat));
            return;
        }
        messages.send(sender, "stats.top-header", gameName, Placeholder.component("stat", statName(stat)));
        var entries = leaderboards.top(gametypeId(game.get()), gameId, stat);
        if (entries.isEmpty()) {
            messages.send(sender, "stats.top-none");
        }
        for (var position = 0; position < entries.size(); position++) {
            var entry = entries.get(position);
            messages.send(sender, "stats.top-entry", Formatter.number("position", position + 1),
                    entry.name().map(name -> Placeholder.unparsed("player", name))
                            .orElseGet(() -> Placeholder.component("player", messages.render("stats.unknown-name"))),
                    Formatter.number("amount", entry.amount()));
        }
    }

    private void show(CommandSender sender, String name, Profile profile, Optional<String> gameId) {
        var player = Placeholder.unparsed("player", name);
        // Checked here, after a lookup, since a reload may have removed the game in the meantime.
        var game = gameId.flatMap(id -> findGame(sender, id));
        if (gameId.isPresent() && game.isEmpty()) {
            return;
        }
        if (game.isPresent()) {
            var totals = profile.mode(gametypeId(game.get()), game.get().id());
            messages.send(sender, "stats.game-header", player, Placeholder.parsed("game", game.get().name()));
            messages.send(sender, "stats.game-totals", totals(totals));
            var stats = statIds(game.get());
            totals.stats().keySet().forEach(stat -> stats.add(stat.id()));
            stats.forEach(stat -> messages.send(sender, "stats.stat",
                    Placeholder.component("stat", statName(stat)), Formatter.number("amount", totals.stat(new Stat(stat)))));
            return;
        }
        messages.send(sender, "stats.header", player);
        if (profile.games().isEmpty()) {
            messages.send(sender, "stats.none");
        }
        profile.games().forEach(gametype -> messages.send(sender, "stats.gametype", Placeholder.unparsed("gametype", gametype), totals(profile.game(gametype))));
    }

    private static TagResolver totals(Totals totals) {
        var place = totals.averagePlace();
        return TagResolver.resolver(Formatter.number("played", totals.played()), Formatter.number("wins", totals.wins()),
                Placeholder.unparsed("place", place.isPresent() ? "%.1f".formatted(place.getAsDouble()) : "-"),
                Formatter.number("left", totals.left()), Formatter.number("minutes", totals.timePlayed().toMinutes()));
    }

    private Component statName(String stat) {
        return messages.render("stats.name." + stat);
    }

    private Optional<Game> findGame(CommandSender sender, String gameId) {
        var game = games.get().find(gameId);
        if (game.isEmpty()) {
            messages.send(sender, "stats.unknown-game", Placeholder.unparsed("game", gameId));
        }
        return game;
    }
}
