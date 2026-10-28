package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.mode.Mode;
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
 * Shows a player's stats, per game or for one mode, and the leaderboards. A player online comes
 * from the profile cache; anyone else is looked up by name in the background. A stat's display
 * name is the message {@code stats.name.<stat>}.
 *
 * <p>Call the public methods from the server thread.
 */
public final class StatsView {

    private final PlayerProfiles profiles;
    private final Leaderboards leaderboards;
    private final Supplier<IdRegistry<Mode>> modes;
    private final Server server;
    private final Messages messages;
    private final Logger logger;

    public StatsView(PlayerProfiles profiles, Leaderboards leaderboards, Supplier<IdRegistry<Mode>> modes, Server server, Messages messages, Logger logger) {
        this.profiles = profiles;
        this.leaderboards = leaderboards;
        this.modes = modes;
        this.server = server;
        this.messages = messages;
        this.logger = logger;
    }

    public List<String> onlineNames() {
        return server.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    public SortedSet<String> modeIds() {
        return modes.get().ids();
    }

    /** The stats the mode has a board for, plus those it keeps, so a new mode can be looked at before anyone played it. */
    public Set<String> boardStats(String modeId) {
        var stats = new TreeSet<String>();
        modes.get().find(modeId).ifPresent(mode -> {
            stats.add(Leaderboards.WINS);
            mode.stats().forEach(stat -> stats.add(stat.id()));
            stats.addAll(leaderboards.stats(mode.game().id(), mode.id()));
        });
        return stats;
    }

    /** @param modeId empty for the totals of every game they played */
    public void show(CommandSender sender, Player target, Optional<String> modeId) {
        profiles.of(target.getUniqueId()).ifPresentOrElse(
                profile -> show(sender, target.getName(), profile, modeId),
                () -> messages.send(sender, "stats.loading", Placeholder.unparsed("player", target.getName())));
    }

    /** @param name of a player online or not, in any case */
    public void show(CommandSender sender, String name, Optional<String> modeId) {
        if (modeId.isPresent() && findMode(sender, modeId.get()).isEmpty()) {
            return;
        }
        var online = server.getPlayerExact(name);
        if (online != null && profiles.of(online.getUniqueId()).isPresent()) {
            show(sender, online, modeId);
            return;
        }
        profiles.find(name).whenComplete((found, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not look up the stats of " + name, failure);
                messages.send(sender, "stats.failed");
            } else if (found.isEmpty()) {
                messages.send(sender, "stats.unknown-player", Placeholder.unparsed("player", name));
            } else {
                show(sender, found.get().name(), found.get().profile(), modeId);
            }
        });
    }

    public void top(CommandSender sender, String modeId, String stat) {
        var mode = findMode(sender, modeId);
        if (mode.isEmpty()) {
            return;
        }
        var modeName = Placeholder.parsed("mode", mode.get().name());
        if (!boardStats(modeId).contains(stat)) {
            messages.send(sender, "stats.unknown-stat", modeName, Placeholder.unparsed("stat", stat));
            return;
        }
        messages.send(sender, "stats.top-header", modeName, Placeholder.component("stat", statName(stat)));
        var entries = leaderboards.top(mode.get().game().id(), modeId, stat);
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

    private void show(CommandSender sender, String name, Profile profile, Optional<String> modeId) {
        var player = Placeholder.unparsed("player", name);
        // Checked here, after a lookup, since a reload may have removed the mode in the meantime.
        var mode = modeId.flatMap(id -> findMode(sender, id));
        if (modeId.isPresent() && mode.isEmpty()) {
            return;
        }
        if (mode.isPresent()) {
            var totals = profile.mode(mode.get().game().id(), mode.get().id());
            messages.send(sender, "stats.mode-header", player, Placeholder.parsed("mode", mode.get().name()));
            messages.send(sender, "stats.mode-totals", totals(totals));
            var stats = new TreeSet<String>();
            mode.get().stats().forEach(stat -> stats.add(stat.id()));
            totals.stats().keySet().forEach(stat -> stats.add(stat.id()));
            stats.forEach(stat -> messages.send(sender, "stats.stat",
                    Placeholder.component("stat", statName(stat)), Formatter.number("amount", totals.stat(new Stat(stat)))));
            return;
        }
        messages.send(sender, "stats.header", player);
        if (profile.games().isEmpty()) {
            messages.send(sender, "stats.none");
        }
        profile.games().forEach(game -> messages.send(sender, "stats.game", Placeholder.unparsed("game", game), totals(profile.game(game))));
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

    private Optional<Mode> findMode(CommandSender sender, String modeId) {
        var mode = modes.get().find(modeId);
        if (mode.isEmpty()) {
            messages.send(sender, "stats.unknown-mode", Placeholder.unparsed("mode", modeId));
        }
        return mode;
    }
}
