package io.github.tst_038.minigami.paper.session;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import io.github.tst_038.minigami.core.presence.Presence;
import io.github.tst_038.minigami.paper.presence.PlayerPresence;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Saves players' state when they join a match and puts it back afterwards. Every snapshot is on disk
 * until it has been restored, so a player who was offline when their match ended, or whose match
 * was lost to a crash, gets it back on their next join.
 */
public final class PlayerSessions implements Listener {

    private final Plugin plugin;
    private final SnapshotStore store;
    private final Logger logger;
    private final PlayerPresence presence;
    /** Snapshots of players in a match. Server thread only. */
    private final Map<UUID, PlayerSnapshot> active = new HashMap<>();
    /** Snapshots found on disk at login, restored at join. */
    private final Map<UUID, PlayerSnapshot> recovered = new ConcurrentHashMap<>();
    /** Players whose snapshot on disk could not be read or restored; kept out of matches so it isn't overwritten. */
    private final Set<UUID> unrecoverable = ConcurrentHashMap.newKeySet();

    /** Players who are held by a game night: their own state is saved once, and matches only park them. Server thread only. */
    private final Map<UUID, Consumer<Player>> held = new HashMap<>();
    /** Holds on players whose snapshot hasn't been given back yet, released once it has, so also across a restore that waits for the next login. Server thread only. */
    private final Map<UUID, Presence.Claim> stateClaims = new HashMap<>();
    /** Where players who quit with their state restored belong; read off the server thread at their next login. */
    private final Map<UUID, PlayerSnapshot.SavedLocation> homes = new ConcurrentHashMap<>();
    /** Players whose quit is being handled: their restore can't be trusted to move them, as the connection is going away. */
    private final Set<UUID> quitting = new HashSet<>();

    /** The loaded worlds, for the login, which runs off the server thread and mustn't ask the server. */
    private final Map<UUID, World> worlds = new ConcurrentHashMap<>();

    public PlayerSessions(Plugin plugin, SnapshotStore store, Logger logger, PlayerPresence presence) {
        plugin.getServer().getWorlds().forEach(world -> worlds.put(world.getUID(), world));
        this.plugin = plugin;
        this.store = store;
        this.logger = logger;
        this.presence = presence;
    }

    /** Whether {@link #take} would succeed: the player has no snapshot left from an earlier match. */
    public boolean canTake(UUID id) {
        return held.containsKey(id) || !active.containsKey(id) && !recovered.containsKey(id) && !unrecoverable.contains(id);
    }

    /**
     * Saves the player's state and returns its restore, to be registered in the match scope. If the
     * player is offline when it runs, they get their state back on their next join instead.
     *
     * A player held by a game night is not saved again, since what they have now is the night's cleared
     * state: their restore only parks them again.
     *
     * @throws IllegalStateException if the player already has a snapshot that hasn't been restored
     */
    public AutoCloseable take(Player player) {
        var id = player.getUniqueId();
        if (!canTake(id)) {
            throw new IllegalStateException(player.getName() + " still has a snapshot that hasn't been restored");
        }
        if (held.containsKey(id)) {
            return () -> {
                var park = held.get(id);
                var online = plugin.getServer().getPlayer(id);
                if (park != null && online != null) {
                    park.accept(online);
                }
            };
        }
        var snapshot = PlayerSnapshot.capture(player);
        // A player who came back after quitting a match may have been moved on joining; where they belong is where they were before the match.
        var home = homes.remove(id);
        if (home != null) {
            snapshot = snapshot.withLocation(home);
        }
        var saved = snapshot;
        active.put(id, saved);
        store.save(id, saved);
        stateClaims.put(id, presence.claim(id));
        return () -> {
            // The snapshot may already have been restored at a rejoin; restoring it again would undo what happened since.
            if (!active.remove(id, saved)) {
                return;
            }
            var online = plugin.getServer().getPlayer(id);
            if (online != null) {
                restore(online, saved);
                if (quitting.contains(id)) {
                    homes.put(id, saved.location());
                }
                releaseState(id);
            }
        };
    }

    /**
     * Saves the player's state until the returned restore runs, like {@link #take}, and keeps it for
     * everything in between: matches the player joins only {@code park} them, back where this left
     * them, and don't save or restore anything of their own. The restore also ends the hold.
     */
    public AutoCloseable hold(Player player, Consumer<Player> park) {
        var id = player.getUniqueId();
        var restore = take(player);
        held.put(id, park);
        return () -> {
            held.remove(id);
            restore.close();
        };
    }

    /** Counts the player as in a match without saving anything: they wait in place, and their state is saved when play starts. */
    public Presence.Claim claimPlace(UUID id) {
        return presence.claim(id);
    }

    private void releaseState(UUID id) {
        var claim = stateClaims.remove(id);
        if (claim != null) {
            claim.close();
        }
    }

    /**
     * Clears what a match or night puts a player through: items, effects, health and the game mode.
     * Call it before teleporting.
     */
    public void prepare(Player player) {
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

    /** Whether the player is in a match with their state saved; it comes back when they leave, so staff mustn't put any back. */
    public boolean isInMatch(UUID id) {
        return active.containsKey(id);
    }

    /** For staff: puts back the snapshot still on disk, e.g. once its broken file was fixed. Not while {@link #isInMatch}. */
    public void restoreSaved(Player player, PlayerSnapshot snapshot) {
        recovered.remove(player.getUniqueId());
        restore(player, snapshot);
        unrecoverable.remove(player.getUniqueId());
        releaseState(player.getUniqueId());
    }

    /**
     * For staff: puts back the snapshot restored last. The state it replaces becomes the last one, so
     * doing it again undoes it. Not while {@link #isInMatch} or a snapshot is still on disk.
     */
    public void restoreLast(Player player, PlayerSnapshot last) {
        var current = PlayerSnapshot.capture(player);
        apply(player, last);
        store.saveLast(player.getUniqueId(), current);
    }

    /** Snapshots of players currently in a match. */
    public int pending() {
        return active.size();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        var id = event.getUniqueId();
        try {
            store.load(id).ifPresent(snapshot -> recovered.put(id, snapshot));
        } catch (IllegalStateException e) {
            unrecoverable.add(id);
            logger.log(Level.SEVERE, "The saved state of " + event.getName() + " can't be restored; its file is kept", e);
        }
    }

    // Around the matches' own handler (normal priority), so their restore knows the player is quitting.
    @EventHandler(priority = EventPriority.LOW)
    public void onQuitStart(PlayerQuitEvent event) {
        homes.remove(event.getPlayer().getUniqueId());
        quitting.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuitEnd(PlayerQuitEvent event) {
        quitting.remove(event.getPlayer().getUniqueId());
    }

    /** Players who quit, or whose state is waiting on disk, log in where they belong. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        var id = event.getConnection().getProfile().getId();
        if (id == null) {
            return;
        }
        var home = homes.get(id);
        if (home == null) {
            var snapshot = recovered.get(id);
            home = snapshot == null ? null : snapshot.location();
        }
        // A world that is gone leaves the server's own choice of spawn.
        var world = home == null ? null : worlds.get(home.world());
        if (world != null) {
            event.setSpawnLocation(home.at(world));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        worlds.put(event.getWorld().getUID(), event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        worlds.remove(event.getWorld().getUID());
    }

    // Once a rejoin has had its say, where they belong is no longer needed.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinEnd(PlayerJoinEvent event) {
        homes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var snapshot = recovered.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        active.remove(player.getUniqueId());
        held.remove(player.getUniqueId());
        try {
            restore(player, snapshot);
            releaseState(player.getUniqueId());
        } catch (RuntimeException e) {
            unrecoverable.add(player.getUniqueId());
            logger.log(Level.SEVERE, "Could not restore the saved state of " + player.getName() + "; its file is kept", e);
        }
    }

    // The file is only retired once the restore went through, so a failed restore is retried at the next join.
    private void restore(Player player, PlayerSnapshot snapshot) {
        apply(player, snapshot);
        store.retire(player.getUniqueId());
    }

    private void apply(Player player, PlayerSnapshot snapshot) {
        snapshot.restore(player);
        // Max health from equipment is applied a tick after the inventory is set, so health may have been clamped.
        // At shutdown there is no next tick, and the clamped value is what gets saved.
        if (plugin.isEnabled()) {
            player.getScheduler().run(plugin, task -> snapshot.restoreHealth(player), null);
        }
    }
}
