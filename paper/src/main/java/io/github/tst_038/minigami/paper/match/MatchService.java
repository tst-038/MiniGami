package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * Puts players into matches and takes them out again. For now there is one game on one arena, so
 * at most one match at a time.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class MatchService {

    private final Plugin plugin;
    private final GameType game;
    private final Supplier<Location> spawn;
    private final PlayerSessions sessions;
    private final Messages messages;
    private final ActiveMatches matches;
    private LiveMatch current;

    public MatchService(Plugin plugin, GameType game, Supplier<Location> spawn, PlayerSessions sessions, Messages messages,
            ActiveMatches matches) {
        this.plugin = plugin;
        this.game = game;
        this.spawn = spawn;
        this.sessions = sessions;
        this.messages = messages;
        this.matches = matches;
    }

    public void join(Player player) {
        if (find(player.getUniqueId()).isPresent()) {
            messages.send(player, "join.already-in-match");
            return;
        }
        var match = openMatch();
        if (!match.isJoinable()) {
            messages.send(player, "join.match-in-progress");
            return;
        }
        try {
            match.join(player);
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.WARNING, player.getName() + " could not join", e);
            messages.send(player, "join.failed");
        }
    }

    public void leave(Player player) {
        var match = find(player.getUniqueId());
        if (match.isEmpty()) {
            messages.send(player, "leave.not-in-match");
            return;
        }
        match.get().leave(player);
    }

    public void forceStart(CommandSender sender) {
        if (current == null || !current.isJoinable() || current.match().roster().size() == 0) {
            messages.send(sender, "start.nothing-to-start");
            return;
        }
        current.forceStart();
        messages.send(sender, "start.started");
    }

    /** What is running, and how many listeners the plugin has, to spot leaks after matches end. */
    public void debug(CommandSender sender) {
        messages.send(sender, "debug.summary", Formatter.number("matches", matches.all().size()),
                Formatter.number("listeners", HandlerList.getRegisteredListeners(plugin).size()),
                Formatter.number("snapshots", sessions.pending()));
        if (current != null && !current.match().isFinished()) {
            var match = current.match();
            messages.send(sender, "debug.match",
                    Placeholder.unparsed("game", game.id()),
                    Placeholder.unparsed("phase", match.currentPhase().map(phase -> phase.id()).orElse("-")),
                    Formatter.number("members", current.members().size()),
                    Formatter.number("alive", match.roster().alive().size()),
                    Formatter.number("tick", match.clock().elapsedTicks()));
        }
    }

    private Optional<LiveMatch> find(UUID id) {
        return Optional.ofNullable(current).filter(match -> !match.match().isFinished() && match.isMember(id));
    }

    private LiveMatch openMatch() {
        if (current == null || current.match().isFinished()) {
            var match = new LiveMatch(plugin, game, spawn.get(), sessions, messages);
            try {
                match.start();
            } catch (RuntimeException e) {
                match.match().dispose();
                throw e;
            }
            matches.add(match.match());
            current = match;
        }
        return current;
    }
}
