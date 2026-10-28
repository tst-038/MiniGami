package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaMaps;
import io.github.tst_038.minigami.paper.mode.Mode;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickEvent;
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
 * Puts players into matches and takes them out again. An arena hosts one match at a time and gets its
 * saved map pasted back after it; joining a mode fills its waiting match first and otherwise opens
 * one in the first free arena of the mode.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class MatchService implements Listener {

    private final Plugin plugin;
    private final Supplier<IdRegistry<Mode>> modes;
    private final Supplier<Optional<Location>> lobby;
    private final PlayerSessions sessions;
    private final Messages messages;
    private final ActiveMatches matches;
    private final ArenaMaps maps;
    /** Per arena id, its latest match; finished ones are dropped lazily. */
    private final Map<String, LiveMatch> byArena = new LinkedHashMap<>();

    /**
     * @param modes the modes currently loaded; config loading replaces the registry
     * @param lobby the lobby currently set
     */
    public MatchService(Plugin plugin, Supplier<IdRegistry<Mode>> modes, Supplier<Optional<Location>> lobby, PlayerSessions sessions, Messages messages,
            ActiveMatches matches, ArenaMaps maps) {
        this.plugin = plugin;
        this.modes = modes;
        this.lobby = lobby;
        this.sessions = sessions;
        this.messages = messages;
        this.matches = matches;
        this.maps = maps;
    }

    public SortedSet<String> modeIds() {
        return modes.get().ids();
    }

    public boolean isArenaInUse(String arenaId) {
        return running().stream().anyMatch(match -> match.arena().id().equals(arenaId));
    }

    public void join(Player player, String modeId) {
        join(player, modeId, player);
    }

    /** An admin puts a player in a match of the mode; the admin hears why if that can't be done. */
    public void join(CommandSender sender, Player player, String modeId) {
        join(player, modeId, sender).ifPresent(match -> messages.send(sender, "match.admin.added",
                Placeholder.component("player", player.name()), Placeholder.unparsed("match", match.arena().id())));
    }

    /** @param feedback hears why the player can't join */
    private Optional<LiveMatch> join(Player player, String modeId, Audience feedback) {
        if (find(player.getUniqueId()).isPresent()) {
            messages.send(feedback, "join.already-in-match", Placeholder.component("player", player.name()));
            return Optional.empty();
        }
        var mode = modes.get().find(modeId);
        if (mode.isEmpty()) {
            messages.send(feedback, "join.unknown-mode", Placeholder.unparsed("mode", modeId));
            return Optional.empty();
        }
        var match = matchToJoin(mode.get());
        if (match.isEmpty()) {
            messages.send(feedback, "join.no-free-arena", Placeholder.parsed("mode", mode.get().name()));
            return Optional.empty();
        }
        try {
            match.get().join(player);
            return match;
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.WARNING, player.getName() + " could not join", e);
            messages.send(feedback, "join.failed", Placeholder.component("player", player.name()));
            return Optional.empty();
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

    /** The modes that can be joined, with how many play each; a player can click one to join. */
    public void list(CommandSender sender) {
        var joinable = modes.get().ids().stream().map(id -> modes.get().find(id).orElseThrow())
                .filter(mode -> !mode.arenas().isEmpty()).toList();
        if (joinable.isEmpty()) {
            messages.send(sender, "list.none");
            return;
        }
        messages.send(sender, "list.header");
        for (var mode : joinable) {
            var players = running().stream().filter(match -> match.mode().id().equals(mode.id()))
                    .mapToInt(match -> match.members().size()).sum();
            messages.send(sender, "list.mode", Placeholder.parsed("mode", mode.name()), Placeholder.unparsed("id", mode.id()),
                    Formatter.number("players", players), Placeholder.styling("join", ClickEvent.runCommand("/mg join " + mode.id())));
        }
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
                    Placeholder.unparsed("game", live.mode().game().id()),
                    Placeholder.unparsed("mode", live.mode().id()),
                    Placeholder.unparsed("arena", live.arena().id()),
                    Placeholder.unparsed("phase", match.currentPhase().map(phase -> phase.id()).orElse("-")),
                    Formatter.number("members", live.members().size()),
                    Formatter.number("alive", match.roster().alive().size()),
                    Formatter.number("tick", match.clock().elapsedTicks()));
        }
    }

    /**
     * A player who quits leaves their match, or is away from it if the mode gives them time to come back. Bukkit saves the player after this event, so their restored state is
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
    Optional<LiveMatch> find(UUID id) {
        return running().stream().filter(match -> match.isMember(id)).findFirst();
    }

    /** The mode's match that is still gathering players and has room, or a new one in a free arena. */
    private Optional<LiveMatch> matchToJoin(Mode mode) {
        var waiting = running().stream()
                .filter(match -> match.mode().id().equals(mode.id()) && match.isJoinable() && match.hasRoom())
                .findFirst();
        if (waiting.isPresent()) {
            return waiting;
        }
        return mode.arenas().stream()
                .filter(arena -> !byArena.containsKey(arena.id()) && !maps.isResetting(arena.id()))
                .findFirst()
                .map(arena -> open(mode, arena));
    }

    private LiveMatch open(Mode mode, Arena arena) {
        var match = new LiveMatch(plugin, mode, arena, lobby.get(), sessions, messages);
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
}
