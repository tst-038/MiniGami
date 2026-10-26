package io.github.tst_038.minigami.paper.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    /** Snapshots of players in a match. Server thread only. */
    private final Map<UUID, PlayerSnapshot> active = new HashMap<>();
    /** Snapshots found on disk at login, restored at join. */
    private final Map<UUID, PlayerSnapshot> recovered = new ConcurrentHashMap<>();
    /** Players whose snapshot on disk could not be read or restored; kept out of matches so it isn't overwritten. */
    private final Set<UUID> unrecoverable = ConcurrentHashMap.newKeySet();

    public PlayerSessions(Plugin plugin, SnapshotStore store, Logger logger) {
        this.plugin = plugin;
        this.store = store;
        this.logger = logger;
    }

    /**
     * Saves the player's state and returns its restore, to be registered in the match scope. If the
     * player is offline when it runs, they get their state back on their next join instead.
     *
     * @throws IllegalStateException if the player already has a snapshot that hasn't been restored
     */
    public AutoCloseable take(Player player) {
        var id = player.getUniqueId();
        if (active.containsKey(id) || recovered.containsKey(id) || unrecoverable.contains(id)) {
            throw new IllegalStateException(player.getName() + " still has a snapshot that hasn't been restored");
        }
        var snapshot = PlayerSnapshot.capture(player);
        active.put(id, snapshot);
        store.save(id, snapshot);
        return () -> {
            // The snapshot may already have been restored at a rejoin; restoring it again would undo what happened since.
            if (!active.remove(id, snapshot)) {
                return;
            }
            var online = plugin.getServer().getPlayer(id);
            if (online != null) {
                restore(online, snapshot);
            }
        };
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var snapshot = recovered.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        active.remove(player.getUniqueId());
        try {
            restore(player, snapshot);
        } catch (RuntimeException e) {
            unrecoverable.add(player.getUniqueId());
            logger.log(Level.SEVERE, "Could not restore the saved state of " + player.getName() + "; its file is kept", e);
        }
    }

    // The file is only deleted once the restore went through, so a failed restore is retried at the next join.
    private void restore(Player player, PlayerSnapshot snapshot) {
        snapshot.restore(player);
        store.delete(player.getUniqueId());
        // Max health from equipment is applied a tick after the inventory is set, so health may have been clamped.
        // At shutdown there is no next tick, and the clamped value is what gets saved.
        if (plugin.isEnabled()) {
            player.getScheduler().run(plugin, task -> snapshot.restoreHealth(player), null);
        }
    }
}
