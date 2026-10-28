package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.stats.Profile.ModeId;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The profiles of the players online, loaded from storage in the background when they join and
 * dropped when they quit, so showing stats never waits on the database. Only used on the server
 * thread.
 */
public final class PlayerProfiles implements Listener {

    private final Storage storage;
    private final Executor serverThread;
    private final Logger logger;
    /** Who a load is for; a load that finishes after they quit is thrown away. */
    private final Set<UUID> online = new HashSet<>();
    private final Map<UUID, Profile> profiles = new HashMap<>();

    public PlayerProfiles(Storage storage, Executor serverThread, Logger logger) {
        this.storage = storage;
        this.serverThread = serverThread;
        this.logger = logger;
    }

    /** Empty while it is still loading, or if it couldn't be. */
    public Optional<Profile> of(UUID id) {
        return Optional.ofNullable(profiles.get(id));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    void forget(UUID id) {
        online.remove(id);
        profiles.remove(id);
    }

    /**
     * Loads the player's profile again, replacing the one in the cache when done. Storage runs its
     * work in order, so a load asked for after a write sees it.
     */
    public void load(UUID id) {
        online.add(id);
        storage.submit(connection -> read(connection, id)).whenCompleteAsync((profile, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not load the stats of " + id, failure);
            } else if (online.contains(id)) {
                profiles.put(id, profile);
            }
        }, serverThread);
    }

    /** Reloads those of the players who are online. */
    void reload(Set<UUID> ids) {
        ids.stream().filter(online::contains).forEach(this::load);
    }

    private static Profile read(Connection connection, UUID id) throws SQLException {
        var stats = new HashMap<ModeId, Map<Stat, Long>>();
        try (var select = connection.prepareStatement("SELECT game, mode, stat, amount FROM player_stat_totals WHERE player = ?")) {
            select.setString(1, id.toString());
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    stats.computeIfAbsent(new ModeId(rows.getString(1), rows.getString(2)), key -> new HashMap<>())
                            .put(new Stat(rows.getString(3)), rows.getLong(4));
                }
            }
        }
        var byMode = new HashMap<ModeId, Totals>();
        try (var select = connection.prepareStatement(
                "SELECT game, mode, played, wins, placed, place_sum, left_matches, seconds_played FROM player_totals WHERE player = ?")) {
            select.setString(1, id.toString());
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    var key = new ModeId(rows.getString(1), rows.getString(2));
                    byMode.put(key, new Totals(rows.getLong(3), rows.getLong(4), rows.getLong(5), rows.getLong(6), rows.getLong(7),
                            Duration.ofSeconds(rows.getLong(8)), stats.getOrDefault(key, Map.of())));
                }
            }
        }
        return new Profile(byMode);
    }
}
