package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.PhaseFeatures;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.mode.Mode;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * A match of a mode in one arena, together with the players in it. Players join during the first
 * phase, up to the mode's maximum, and get their own state back when they leave or the match ends.
 *
 * <p>Where they wait depends on the mode: in place, untouched until the start; or in the lobby or
 * the arena, with their state saved and already prepared.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class LiveMatch {

    private static final long START_DELAY_TICKS = 5 * 20;

    private final Plugin plugin;
    private final Mode mode;
    private final Arena arena;
    /** Where joining players are sent; empty when they wait in place. */
    private final Optional<Location> waitingSpot;
    private final PlayerSessions sessions;
    private final Messages messages;
    private final Match match;
    /** Players currently in the match, eliminated ones included; the roster also keeps those who left. */
    private final Set<UUID> members = new LinkedHashSet<>();
    /** Members whose state has been saved; players waiting in place have none until the start. */
    private final Map<UUID, AutoCloseable> restores = new HashMap<>();
    /** Deaths per participant since the start, against the mode's lives. */
    private final Map<UUID, Integer> deaths = new HashMap<>();
    // shortcut: only kept in memory, so a crash leaves the arena changed; fine until matches get their own world.
    private final Map<Block, BlockState> originals = new LinkedHashMap<>();
    private MatchClock.Task startDelay;

    /**
     * @param arena must be playable
     * @param lobby where players wait in a mode that waits in the lobby
     * @throws IllegalArgumentException if the mode waits in the lobby and there is none
     */
    public LiveMatch(Plugin plugin, Mode mode, Arena arena, Optional<Location> lobby, PlayerSessions sessions, Messages messages) {
        this.plugin = plugin;
        this.mode = mode;
        this.arena = arena;
        this.waitingSpot = switch (mode.waiting()) {
            case IN_PLACE -> Optional.empty();
            case LOBBY -> Optional.of(lobby.orElseThrow(() -> new IllegalArgumentException(mode.id() + " waits in a lobby, but none is set")));
            case ARENA -> Optional.of(arena.waitingSpot().orElseGet(() -> arena.spawns().getFirst()));
        };
        this.sessions = sessions;
        this.messages = messages;
        var features = new PhaseFeatures(features(mode), (phase, scope) -> new PaperMatchContext(plugin, this, scope));
        this.match = new Match(mode.game().phases(), mode.game().winCondition(), features);
    }

    /** The game's own features per phase, followed by those the mode configures. */
    private static Map<String, List<Feature>> features(Mode mode) {
        var merged = new HashMap<String, List<Feature>>();
        mode.game().features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        mode.features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        return merged;
    }

    public void start() {
        var context = new PaperMatchContext(plugin, this, match.scope());
        context.on(PlayerDeathEvent.class, this::onDeath);
        context.on(PlayerRespawnEvent.class, this::onRespawn);
        // Without physics, so a restored block doesn't make its neighbours fall or flow before they are back too.
        match.scope().register(() -> originals.values().forEach(state -> state.update(true, false)));
        match.start();
    }

    public Match match() {
        return match;
    }

    public Mode mode() {
        return mode;
    }

    public Arena arena() {
        return arena;
    }

    public boolean isMember(UUID id) {
        return members.contains(id);
    }

    /** Whether the player's events belong to the match: a member who no longer waits in place. */
    public boolean isActive(UUID id) {
        return restores.containsKey(id);
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public Messages messages() {
        return messages;
    }

    /** See {@link io.github.tst_038.minigami.api.feature.MatchContext#rememberOriginal(Block)}. */
    public void rememberOriginal(BlockState original) {
        originals.putIfAbsent(original.getBlock(), original);
    }

    public Audience audience() {
        var server = plugin.getServer();
        return Audience.audience(members.stream().map(server::getPlayer).filter(Objects::nonNull).toList());
    }

    /** Whether the match is still gathering players, i.e. in its first phase. */
    public boolean isJoinable() {
        var first = mode.game().phases().getFirst();
        return !match.isFinished() && match.currentPhase().filter(first::equals).isPresent();
    }

    /** Whether another player fits in before the mode's maximum is reached. */
    public boolean hasRoom() {
        return match.roster().size() < mode.maxPlayers();
    }

    /**
     * Adds the player. Unless they wait in place, their state is saved now, they are prepared for
     * the match and sent to where they wait.
     *
     * @throws IllegalStateException if the match can't be joined or is full, or the player still has saved state
     *     from an earlier match that wasn't restored
     */
    public void join(Player player) {
        if (!isJoinable() || !hasRoom()) {
            throw new IllegalStateException("The match is not accepting players");
        }
        var id = player.getUniqueId();
        if (!sessions.canTake(id)) {
            throw new IllegalStateException(player.getName() + " still has a snapshot that hasn't been restored");
        }
        if (waitingSpot.isPresent()) {
            saveState(player);
            prepare(player);
            player.teleport(waitingSpot.get());
        }
        match.roster().add(id);
        members.add(id);
        messages.send(audience(), "match.joined", Placeholder.component("player", player.name()),
                Formatter.number("players", match.roster().size()), Formatter.number("needed", mode.minPlayers()));
        if (startDelay == null && match.roster().size() >= mode.minPlayers()) {
            startDelay = match.clock().schedule(START_DELAY_TICKS, this::startPlaying);
            match.scope().register(startDelay);
            messages.send(audience(), "match.starting", Formatter.number("seconds", START_DELAY_TICKS / 20));
        }
    }

    /**
     * Takes the player out and gives them their own state back. Before the match starts they are
     * removed as if they never joined; after that they count as eliminated.
     *
     * @return {@code false} if they weren't in the match
     */
    public boolean leave(Player player) {
        var id = player.getUniqueId();
        if (!members.remove(id)) {
            return false;
        }
        if (isJoinable()) {
            match.roster().remove(id);
            if (startDelay != null && match.roster().size() < mode.minPlayers()) {
                startDelay.close();
                startDelay = null;
                messages.send(audience(), "match.start-cancelled");
            }
        } else {
            match.eliminate(id);
        }
        restore(id);
        messages.send(audience(), "match.left", Placeholder.component("player", player.name()));
        messages.send(player, "match.you-left");
        if (members.isEmpty()) {
            match.dispose();
        }
        return true;
    }

    /**
     * Starts the match now, without waiting for enough players.
     *
     * @throws IllegalStateException if it already started or nobody joined
     */
    public void forceStart() {
        if (!isJoinable() || match.roster().size() == 0) {
            throw new IllegalStateException("Only a match with players that hasn't started can be started");
        }
        if (startDelay != null) {
            startDelay.close();
        }
        startPlaying();
    }

    /** Brings everyone to the spawns, round robin, prepared for play. */
    private void startPlaying() {
        startDelay = null;
        var spawns = arena.spawns();
        var next = 0;
        for (var id : List.copyOf(members)) {
            var player = plugin.getServer().getPlayer(id);
            if (player == null) {
                continue;
            }
            if (!isActive(id)) {
                try {
                    saveState(player);
                } catch (IllegalStateException e) {
                    plugin.getLogger().log(Level.WARNING, player.getName() + " could not be brought into the match", e);
                    messages.send(player, "join.failed");
                    members.remove(id);
                    match.roster().remove(id);
                    continue;
                }
            }
            prepare(player);
            player.teleport(spawns.get(next++ % spawns.size()));
        }
        if (members.isEmpty()) {
            match.dispose();
            return;
        }
        // After the teleports, so the next phase's features never see players moving from where they waited to their spawn.
        match.advancePhase();
    }

    /**
     * A death stays inside the match: nothing drops, only the match hears of it, and while the match
     * is on it costs a life. The player respawns at once, so nobody sits on the death screen.
     */
    private void onDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        event.getDrops().clear();
        event.setShouldDropExperience(false);
        var message = event.deathMessage();
        event.deathMessage(null);
        if (message != null) {
            audience().sendMessage(message);
        }
        var id = player.getUniqueId();
        var playing = !isJoinable() && match.result().isEmpty();
        if (playing && mode.lives() > 0 && match.roster().find(id).filter(Participant::isAlive).isPresent()
                && deaths.merge(id, 1, Integer::sum) >= mode.lives()) {
            messages.send(audience(), "match.eliminated", Placeholder.component("player", player.name()));
            match.eliminate(id);
        }
        player.getScheduler().run(plugin, task -> {
            if (player.isDead()) {
                player.spigot().respawn();
            }
        }, null);
    }

    /** Before the start back to where they wait; after it to a random spawn, as a spectator once they are out. */
    private void onRespawn(PlayerRespawnEvent event) {
        if (isJoinable()) {
            waitingSpot.ifPresent(event::setRespawnLocation);
            return;
        }
        var spawns = arena.spawns();
        event.setRespawnLocation(spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())));
        var player = event.getPlayer();
        if (match.roster().find(player.getUniqueId()).filter(Participant::isAlive).isEmpty()) {
            // The game mode is reset during the respawn, so it is set right after.
            player.getScheduler().run(plugin, task -> player.setGameMode(GameMode.SPECTATOR), null);
        }
    }

    private void saveState(Player player) {
        var restore = sessions.take(player);
        match.scope().register(restore);
        restores.put(player.getUniqueId(), restore);
    }

    /** Players who waited in place had nothing changed, so there is nothing to give back. */
    private void restore(UUID id) {
        var restore = restores.remove(id);
        if (restore == null) {
            return;
        }
        try {
            restore.close();
        } catch (Exception e) {
            // The snapshot stays on disk, so the player gets their state back on their next join.
            plugin.getLogger().log(Level.SEVERE, "Could not restore the state of " + id, e);
        }
    }

    private void prepare(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setExhaustion(0);
        player.setLevel(0);
        player.setExp(0);
        player.clearActivePotionEffects();
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0);
    }
}
