package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.match.MatchEndEvent;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.storage.Storage;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Keeps every match that gets a result: the match with its players and their stats, and each
 * player's running totals and per-day stats (played and wins included) for the game and variant, in one
 * transaction, so a crash leaves all of it or none. A day is the epoch day in the clock's time zone. The profiles of its players who are online are loaded again after.
 */
public final class StatsRecorder implements Listener {

    private final Storage storage;
    private final PlayerProfiles profiles;
    private final Clock clock;
    private final Logger logger;

    public StatsRecorder(Storage storage, PlayerProfiles profiles, Clock clock, Logger logger) {
        this.storage = storage;
        this.profiles = profiles;
        this.clock = clock;
        this.logger = logger;
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        var record = event.record();
        record(record).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not store the match " + record, failure);
            return null;
        });
        profiles.reload(record.participants().stream().map(MatchRecord.Entry::id).collect(Collectors.toSet()));
    }

    /** Stores the match in the background; the record is immutable, so it is safe to hand over. */
    public CompletableFuture<Void> record(MatchRecord record) {
        return storage.submit(connection -> {
            var matchId = insertMatch(connection, record);
            var places = new HashMap<UUID, Integer>();
            record.result().placements().forEach(placement -> placement.participants().forEach(id -> places.put(id, placement.place())));
            // shortcut: everyone is counted as playing the whole match, those who left too; keep leave times in the record when that matters.
            var day = LocalDate.ofInstant(record.end(), clock.getZone()).toEpochDay();
            var seconds = Duration.between(record.start(), record.end()).toSeconds();
            var game = record.game();
            var variant = variant(record);
            for (var entry : record.participants()) {
                var place = places.get(entry.id());
                insertPlayer(connection, matchId, entry, place);
                var won = place != null && place == 1;
                StatRows.addMatchTotals(connection, entry.id(), game, variant, 1, won ? 1 : 0, place == null ? 0 : 1, place == null ? 0 : place,
                        entry.left() ? 1 : 0, seconds);
                for (var stat : entry.stats().entrySet()) {
                    var id = stat.getKey().id();
                    insertStat(connection, matchId, entry.id(), id, stat.getValue());
                    StatRows.addTotal(connection, entry.id(), game, variant, id, stat.getValue());
                    StatRows.addDay(connection, entry.id(), game, variant, id, day, stat.getValue());
                }
                StatRows.addDay(connection, entry.id(), game, variant, Stat.PLAYED, day, 1);
                if (won) {
                    StatRows.addDay(connection, entry.id(), game, variant, Stat.WINS, day, 1);
                }
            }
            return null;
        });
    }

    private static long insertMatch(Connection connection, MatchRecord record) throws SQLException {
        try (var insert = connection.prepareStatement(
                "INSERT INTO matches (gametype, game, variant, arena, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, record.gametype());
            insert.setString(2, record.game());
            insert.setString(3, variant(record));
            insert.setString(4, record.arena());
            insert.setLong(5, record.start().toEpochMilli());
            insert.setLong(6, record.end().toEpochMilli());
            insert.executeUpdate();
            try (var keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** As stored: '' for the game itself, so it can be part of a key. */
    private static String variant(MatchRecord record) {
        return record.variant().orElse("");
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
}
