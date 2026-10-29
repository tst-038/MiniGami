package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaMaps;
import io.github.tst_038.minigami.paper.game.Variant;
import io.github.tst_038.minigami.paper.hud.MatchValues;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * The matches on the server, whoever opened them. An arena hosts one match at a time and gets its
 * saved map pasted back after it. Match sources, such as {@link PublicMatches} for {@code /mg join},
 * open matches here and put players in them.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class RunningMatches implements Listener {

    private final Plugin plugin;
    private final Supplier<Optional<Location>> lobby;
    private final PlayerSessions sessions;
    private final Messages messages;
    private final ActiveMatches matches;
    private final ArenaMaps maps;
    private final MatchValues values;
    /** Per arena id, its latest match; finished ones are dropped lazily. */
    private final Map<String, LiveMatch> byArena = new LinkedHashMap<>();

    /** @param lobby the lobby currently set */
    public RunningMatches(Plugin plugin, Supplier<Optional<Location>> lobby, PlayerSessions sessions, Messages messages,
            ActiveMatches matches, ArenaMaps maps, MatchValues values) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.sessions = sessions;
        this.messages = messages;
        this.matches = matches;
        this.maps = maps;
        this.values = values;
    }

    /**
     * Opens a match of the variant in its first free arena, waiting for players.
     *
     * @return empty if every arena of the variant is in use or being reset
     */
    public Optional<LiveMatch> open(Variant variant, Opening opening) {
        return variant.arenas().stream()
                .filter(arena -> !byArena.containsKey(arena.id()) && !maps.isResetting(arena.id()))
                .findFirst()
                .map(arena -> open(variant, arena, opening));
    }

    private LiveMatch open(Variant variant, Arena arena, Opening opening) {
        var match = new LiveMatch(plugin, variant, arena, opening, lobby.get(), sessions, messages, values);
        // Registered first so it runs last, once the match has let go of the arena.
        match.match().scope().register(() -> maps.reset(arena, Optional.empty()));
        try {
            match.start();
        } catch (RuntimeException e) {
            match.match().dispose();
            throw e;
        }
        matches.add(match.match());
        byArena.put(arena.id(), match);
        return match;
    }

    public boolean isArenaInUse(String arenaId) {
        return running().stream().anyMatch(match -> match.arena().id().equals(arenaId));
    }

    public void leave(Player player) {
        var match = find(player.getUniqueId());
        if (match.isEmpty()) {
            messages.send(player, "leave.not-in-match");
            return;
        }
        match.get().leave(player);
    }

    /** Starts the sender's own match, or else the first one waiting for players. */
    public void forceStart(CommandSender sender) {
        var own = sender instanceof Player player ? find(player.getUniqueId()) : Optional.<LiveMatch>empty();
        var match = own.or(() -> running().stream().filter(LiveMatch::isJoinable).findFirst())
                .filter(candidate -> candidate.isJoinable() && candidate.match().roster().size() > 0);
        if (match.isEmpty()) {
            messages.send(sender, "start.nothing-to-start");
            return;
        }
        match.get().forceStart();
        messages.send(sender, "start.started");
    }

    /** What is running, and how many listeners the plugin has, to spot leaks after matches end. */
    public void debug(CommandSender sender) {
        messages.send(sender, "debug.summary", Formatter.number("matches", matches.all().size()),
                Formatter.number("listeners", HandlerList.getRegisteredListeners(plugin).size()),
                Formatter.number("snapshots", sessions.pending()));
        for (var live : running()) {
            var match = live.match();
            messages.send(sender, "debug.match",
                    Placeholder.unparsed("gametype", live.variant().gametype().id()),
                    Placeholder.unparsed("game", live.variant().key()),
                    Placeholder.unparsed("arena", live.arena().id()),
                    Placeholder.unparsed("phase", match.currentPhase().map(phase -> phase.id()).orElse("-")),
                    Formatter.number("members", live.members().size()),
                    Formatter.number("alive", match.roster().alive().size()),
                    Formatter.number("tick", match.clock().elapsedTicks()));
        }
    }

    /**
     * A player who quits leaves their match, or is away from it if the game gives them time to come back. Bukkit saves the player after this event, so their restored state is
     * what gets saved.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        find(event.getPlayer().getUniqueId()).ifPresent(match -> match.quit(event.getPlayer()));
    }

    /** A participant who quit with time to come back is put back in their match. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        running().stream().filter(match -> match.isAway(player.getUniqueId())).findFirst().ifPresent(match -> {
            try {
                match.rejoin(player);
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.WARNING, player.getName() + " could not be brought back into their match", e);
                messages.send(player, "join.failed", Placeholder.component("player", player.name()));
            }
        });
    }

    /** The matches that haven't finished, oldest first. */
    Collection<LiveMatch> running() {
        byArena.values().removeIf(match -> match.match().isFinished());
        return byArena.values();
    }

    /** The match the player is in. */
    public Optional<LiveMatch> find(UUID id) {
        return running().stream().filter(match -> match.isMember(id)).findFirst();
    }
}
