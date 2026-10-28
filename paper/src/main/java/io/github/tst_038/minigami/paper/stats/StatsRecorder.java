package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.match.MatchEndEvent;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Keeps every match that gets a result: the match with its players and their stats, and each
 * player's running totals for the game and mode, in one transaction, so a crash leaves all of it
 * or none.
 */
public final class StatsRecorder implements Listener {

    private final Storage storage;
    private final Logger logger;

    public StatsRecorder(Storage storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        var record = event.record();
        record(record).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not store the match " + record, failure);
            return null;
        });
    }

    /** Stores the match in the background; the record is immutable, so it is safe to hand over. */
    public CompletableFuture<Void> record(MatchRecord record) {
        return storage.submit(connection -> {
            var matchId = insertMatch(connection, record);
            var places = new HashMap<UUID, Integer>();
            record.result().placements().forEach(placement -> placement.participants().forEach(id -> places.put(id, placement.place())));
            // shortcut: everyone is counted as playing the whole match, those who left too; keep leave times in the record when that matters.
            var seconds = Duration.between(record.start(), record.end()).toSeconds();
            for (var entry : record.participants()) {
                var place = places.get(entry.id());
                insertPlayer(connection, matchId, entry, place);
                addTotals(connection, record, entry.id(), place, entry.left(), seconds);
                for (var stat : entry.stats().entrySet()) {
                    insertStat(connection, matchId, entry.id(), stat.getKey().id(), stat.getValue());
                    addStatTotal(connection, record, entry.id(), stat.getKey().id(), stat.getValue());
                }
            }
            return null;
        });
    }

    private static long insertMatch(Connection connection, MatchRecord record) throws SQLException {
        try (var insert = connection.prepareStatement(
                "INSERT INTO matches (game, mode, arena, started_at, ended_at) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, record.game());
            insert.setString(2, record.mode());
            insert.setString(3, record.arena());
            insert.setLong(4, record.start().toEpochMilli());
            insert.setLong(5, record.end().toEpochMilli());
            insert.executeUpdate();
            try (var keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void insertPlayer(Connection connection, long matchId, MatchRecord.Entry entry, Integer place) throws SQLException {
        try (var insert = connection.prepareStatement(
                "INSERT INTO match_players (match_id, player, team, place, left_match) VALUES (?, ?, ?, ?, ?)")) {
            insert.setLong(1, matchId);
            insert.setString(2, entry.id().toString());
            insert.setString(3, entry.team().orElse(null));
            insert.setObject(4, place);
            insert.setInt(5, entry.left() ? 1 : 0);
            insert.executeUpdate();
        }
    }

    private static void insertStat(Connection connection, long matchId, UUID player, String stat, long amount) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO match_stats (match_id, player, stat, amount) VALUES (?, ?, ?, ?)")) {
            insert.setLong(1, matchId);
            insert.setString(2, player.toString());
            insert.setString(3, stat);
            insert.setLong(4, amount);
            insert.executeUpdate();
        }
    }

    // Each total is updated, or inserted the first time: plain SQL every database understands, and safe since only the storage thread writes.
    private static void addTotals(Connection connection, MatchRecord record, UUID player, Integer place, boolean left, long seconds)
            throws SQLException {
        var won = place != null && place == 1 ? 1 : 0;
        var placed = place == null ? 0 : 1;
        var placeSum = place == null ? 0 : place;
        var gone = left ? 1 : 0;
        try (var update = connection.prepareStatement("""
                UPDATE player_totals SET played = played + 1, wins = wins + ?, placed = placed + ?, place_sum = place_sum + ?,
                    left_matches = left_matches + ?, seconds_played = seconds_played + ?
                WHERE player = ? AND game = ? AND mode = ?""")) {
            update.setInt(1, won);
            update.setInt(2, placed);
            update.setInt(3, placeSum);
            update.setInt(4, gone);
            update.setLong(5, seconds);
            update.setString(6, player.toString());
            update.setString(7, record.game());
            update.setString(8, record.mode());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (var insert = connection.prepareStatement("""
                INSERT INTO player_totals (player, game, mode, played, wins, placed, place_sum, left_matches, seconds_played)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?)""")) {
            insert.setString(1, player.toString());
            insert.setString(2, record.game());
            insert.setString(3, record.mode());
            insert.setInt(4, won);
            insert.setInt(5, placed);
            insert.setInt(6, placeSum);
            insert.setInt(7, gone);
            insert.setLong(8, seconds);
            insert.executeUpdate();
        }
    }

    private static void addStatTotal(Connection connection, MatchRecord record, UUID player, String stat, long amount) throws SQLException {
        try (var update = connection.prepareStatement(
                "UPDATE player_stat_totals SET amount = amount + ? WHERE player = ? AND game = ? AND mode = ? AND stat = ?")) {
            update.setLong(1, amount);
            update.setString(2, player.toString());
            update.setString(3, record.game());
            update.setString(4, record.mode());
            update.setString(5, stat);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO player_stat_totals (player, game, mode, stat, amount) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, record.game());
            insert.setString(3, record.mode());
            insert.setString(4, stat);
            insert.setLong(5, amount);
            insert.executeUpdate();
        }
    }
}
