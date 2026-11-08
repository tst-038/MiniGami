package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.stats.Profile.VariantId;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The profiles of the players online, loaded from storage in the background when they join and
 * dropped when they quit, so showing stats never waits on the database. Also remembers the name
 * each player joined with, so offline players can be found by name. Used on the server thread; {@link #of} on any.
 */
public final class PlayerProfiles implements Listener {

    /** A player found by name, with the name as they last joined with it. */
    public record Named(UUID id, String name, Profile profile) {
    }

    private final Storage storage;
    private final Executor serverThread;
    private final Clock clock;
    private final Logger logger;
    /** Who a load is for; a load that finishes after they quit is thrown away. */
    private final Set<UUID> online = new HashSet<>();
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();

    public PlayerProfiles(Storage storage, Executor serverThread, Clock clock, Logger logger) {
        this.storage = storage;
        this.serverThread = serverThread;
        this.clock = clock;
        this.logger = logger;
    }

    /** Empty while it is still loading, or if it couldn't be. */
    public Optional<Profile> of(UUID id) {
        return Optional.ofNullable(profiles.get(id));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        join(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    /** Remembers their name and loads their profile. */
    public void join(UUID id, String name) {
        var seen = clock.millis();
        storage.submit(connection -> {
            saveName(connection, id, name, seen);
            return null;
        }).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not save the name of " + id, failure);
            return null;
        });
        load(id);
    }

    /**
     * Looks a player up by the name they last joined with, in any case, and reads their profile;
     * completes on the server thread. Empty if nobody joined with that name.
     */
    public CompletableFuture<Optional<Named>> find(String name) {
        return storage.submit(connection -> {
            var found = findName(connection, name);
            return found.isEmpty() ? Optional.<Named>empty()
                    : Optional.of(new Named(found.get().id(), found.get().name(), read(connection, found.get().id())));
        }).thenApplyAsync(found -> found, serverThread);
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
    void load(UUID id) {
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

    /** Reloads every player who is online. */
    void reloadOnline() {
        reload(Set.copyOf(online));
    }

    private record NameRow(UUID id, String name) {
    }

    private static void saveName(Connection connection, UUID id, String name, long seen) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE player_names SET name = ?, name_key = ?, seen_at = ? WHERE player = ?")) {
            update.setString(1, name);
            update.setString(2, name.toLowerCase(Locale.ROOT));
            update.setLong(3, seen);
            update.setString(4, id.toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (var insert = connection.prepareStatement("INSERT INTO player_names (player, name, name_key, seen_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, id.toString());
            insert.setString(2, name);
            insert.setString(3, name.toLowerCase(Locale.ROOT));
            insert.setLong(4, seen);
            insert.executeUpdate();
        }
    }

    private static Optional<NameRow> findName(Connection connection, String name) throws SQLException {
        try (var select = connection.prepareStatement("SELECT player, name FROM player_names WHERE name_key = ? ORDER BY seen_at DESC")) {
            select.setString(1, name.toLowerCase(Locale.ROOT));
            try (var rows = select.executeQuery()) {
                return rows.next() ? Optional.of(new NameRow(UUID.fromString(rows.getString(1)), rows.getString(2))) : Optional.empty();
            }
        }
    }

    private static VariantId variantId(String game, String variant) {
        return new VariantId(game, variant.isEmpty() ? Optional.empty() : Optional.of(variant));
    }

    private static Profile read(Connection connection, UUID id) throws SQLException {
        var stats = new HashMap<VariantId, Map<Stat, Long>>();
        try (var select = connection.prepareStatement("SELECT game, variant, stat, amount FROM player_stat_totals WHERE player = ?")) {
            select.setString(1, id.toString());
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    stats.computeIfAbsent(variantId(rows.getString(1), rows.getString(2)), key -> new HashMap<>())
                            .put(new Stat(rows.getString(3)), rows.getLong(4));
                }
            }
        }
        var byVariant = new HashMap<VariantId, Totals>();
        try (var select = connection.prepareStatement(
                "SELECT game, variant, played, wins, placed, place_sum, left_matches, seconds_played FROM player_totals WHERE player = ?")) {
            select.setString(1, id.toString());
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    var key = variantId(rows.getString(1), rows.getString(2));
                    byVariant.put(key, new Totals(rows.getLong(3), rows.getLong(4), rows.getLong(5), rows.getLong(6), rows.getLong(7),
                            Duration.ofSeconds(rows.getLong(8)), stats.getOrDefault(key, Map.of())));
                }
            }
        }
        return new Profile(byVariant);
    }
}
