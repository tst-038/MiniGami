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
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shows a player's stats, per game or for one game and its variants, and the leaderboards. A player online comes
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
            stats.add(Leaderboards.PLAYED);
            stats.addAll(statIds(game));
            stats.addAll(leaderboards.stats(game.id()));
        });
        return stats;
    }

    /** What every variant keeps, since they are counted together under the game. */
    private static Set<String> statIds(Game game) {
        var stats = new TreeSet<String>();
        game.all().forEach(variant -> variant.stats().forEach(stat -> stats.add(stat.id())));
        return stats;
    }

    /** Empty for a game that isn't loaded. */
    public Set<String> variantIds(String gameId) {
        return games.get().find(gameId).map(game -> game.variants().keySet()).orElse(Set.of());
    }

    /** @param gameId empty for the totals of every game they played */
    public void show(CommandSender sender, Player target, Optional<String> gameId) {
        show(sender, target, gameId, Optional.empty());
    }

    /** @param stat empty for all of them; only with a game */
    public void show(CommandSender sender, Player target, Optional<String> gameId, Optional<String> stat) {
        profiles.of(target.getUniqueId()).ifPresentOrElse(
                profile -> show(sender, target.getName(), profile, gameId, stat),
                () -> messages.send(sender, "stats.loading", Placeholder.unparsed("player", target.getName())));
    }

    /** @param name of a player online or not, in any case */
    public void show(CommandSender sender, String name, Optional<String> gameId) {
        show(sender, name, gameId, Optional.empty());
    }

    /** @param stat empty for all of them; only with a game */
    public void show(CommandSender sender, String name, Optional<String> gameId, Optional<String> stat) {
        if (gameId.isPresent() && findGame(sender, gameId.get()).isEmpty()) {
            return;
        }
        if (stat.isPresent() && !boardStats(gameId.orElseThrow()).contains(stat.get())) {
            messages.send(sender, "stats.unknown-stat", Placeholder.parsed("game", findGame(sender, gameId.get()).orElseThrow().name()),
                    Placeholder.unparsed("stat", stat.get()));
            return;
        }
        var online = server.getPlayerExact(name);
        if (online != null && profiles.of(online.getUniqueId()).isPresent()) {
            show(sender, online, gameId, stat);
            return;
        }
        profiles.find(name).whenComplete((found, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not look up the stats of " + name, failure);
                messages.send(sender, "stats.failed");
            } else if (found.isEmpty()) {
                messages.send(sender, "stats.unknown-player", Placeholder.unparsed("player", name));
            } else {
                show(sender, found.get().name(), found.get().profile(), gameId, stat);
            }
        });
    }

    /** @param variantId empty for the whole game, its variants added up */
    public void top(CommandSender sender, String gameId, String stat, Optional<String> variantId) {
        var game = findGame(sender, gameId);
        if (game.isEmpty()) {
            return;
        }
        var variant = game.get().variant(variantId);
        if (variant.isEmpty()) {
            messages.send(sender, "stats.unknown-variant", Placeholder.parsed("game", game.get().name()),
                    Placeholder.unparsed("variant", variantId.orElseThrow()),
                    Placeholder.unparsed("variants", String.join(", ", game.get().variants().keySet())));
            return;
        }
        var gameName = Placeholder.parsed("game", variant.get().name());
        if (!boardStats(gameId).contains(stat)) {
            messages.send(sender, "stats.unknown-stat", gameName, Placeholder.unparsed("stat", stat));
            return;
        }
        messages.send(sender, "stats.top-header", gameName, Placeholder.component("stat", statName(stat)));
        var entries = leaderboards.top(gameId, variantId, stat);
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

    private void show(CommandSender sender, String name, Profile profile, Optional<String> gameId, Optional<String> only) {
        var player = Placeholder.unparsed("player", name);
        // Checked here, after a lookup, since a reload may have removed the game in the meantime.
        var game = gameId.flatMap(id -> findGame(sender, id));
        if (gameId.isPresent() && game.isEmpty()) {
            return;
        }
        if (game.isPresent()) {
            var totals = profile.game(game.get().id());
            messages.send(sender, "stats.game-header", player, Placeholder.parsed("game", game.get().name()));
            if (only.isEmpty()) {
                sendRecord(sender, totals, true);
            }
            var stats = statIds(game.get());
            totals.stats().keySet().forEach(stat -> stats.add(stat.id()));
            only.ifPresent(stat -> {
                stats.clear();
                stats.add(stat);
            });
            stats.forEach(stat -> messages.send(sender, "stats.stat",
                    Placeholder.component("stat", statName(stat)), Formatter.number("amount", amount(totals, stat))));
            if (only.isEmpty() && !game.get().variants().isEmpty()) {
                // shortcut: a variant since removed from the game's file isn't listed, though it still counts in the totals above.
                game.get().all().forEach(variant -> {
                    var played = profile.variant(variant.game(), variant.id());
                    if (played.played() > 0) {
                        messages.send(sender, "stats.variant", Placeholder.parsed("variant", variant.name()));
                        sendRecord(sender, played, false);
                    }
                });
            }
            return;
        }
        messages.send(sender, "stats.header", player);
        if (profile.games().isEmpty()) {
            messages.send(sender, "stats.none");
        }
        profile.games().forEach(id -> {
            var totals = profile.game(id);
            messages.send(sender, "stats.game-name",
                    games.get().find(id).map(played -> Placeholder.parsed("game", played.name())).orElseGet(() -> Placeholder.unparsed("game", id)));
            sendRecord(sender, totals, false);
            var kept = totals.stats().keySet().stream().map(Stat::id).sorted().map(stat -> messages.render("stats.stat-inline",
                    Placeholder.component("stat", statName(stat)), Formatter.number("amount", totals.stat(new Stat(stat))))).toList();
            if (!kept.isEmpty()) {
                messages.send(sender, "stats.game-stats", Placeholder.component("stats", Component.join(JoinConfiguration.separator(messages.render("stats.separator")), kept)));
            }
        });
    }

    private void sendRecord(CommandSender sender, Totals totals, boolean withLeft) {
        messages.send(sender, "stats.record", totals(totals));
        if (withLeft && totals.left() > 0) {
            messages.send(sender, "stats.left", totals(totals));
        }
    }

    private static long amount(Totals totals, String stat) {
        return switch (stat) {
            case Leaderboards.WINS -> totals.wins();
            case Leaderboards.PLAYED -> totals.played();
            default -> totals.stat(new Stat(stat));
        };
    }

    private static TagResolver totals(Totals totals) {
        var place = totals.averagePlace();
        return TagResolver.resolver(Formatter.number("played", totals.played()), Formatter.number("wins", totals.wins()), Formatter.number("losses", totals.played() - totals.wins()),
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
