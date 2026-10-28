package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchRecord.Entry;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.storage.Migrations;
import io.github.tst_038.minigami.paper.storage.SqliteStorage;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsRecorderTest {

    private static final Stat KILLS = new Stat("kills");
    private static final Instant START = Instant.parse("2026-10-28T20:00:00Z");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    @TempDir
    private Path folder;
    private Storage storage;
    private StatsRecorder recorder;

    @BeforeEach
    void open() {
        // The migrations the plugin ships.
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"),
                Logger.getLogger(getClass().getName()));
        recorder = new StatsRecorder(storage, new PlayerProfiles(storage, Runnable::run, Clock.systemUTC(), Logger.getLogger(getClass().getName())),
                Logger.getLogger(getClass().getName()));
    }

    @AfterEach
    void close() {
        storage.close();
    }

    /** Alice wins with 3 kills, Bob comes second with 1 kill and left; 90 seconds of play. */
    private MatchRecord match(String mode) {
        return new MatchRecord("team-deathmatch", mode, "arena-1", START, START.plusSeconds(90),
                new MatchResult(List.of(new Placement(1, List.of(alice)), new Placement(2, List.of(bob)))),
                List.of(new Entry(alice, Optional.of("red"), false, Map.of(KILLS, 3L)),
                        new Entry(bob, Optional.of("blue"), true, Map.of(KILLS, 1L))));
    }

    private List<String> rows(String sql) {
        return storage.submit(connection -> {
            var rows = new ArrayList<String>();
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                var columns = result.getMetaData().getColumnCount();
                while (result.next()) {
                    var row = new ArrayList<String>();
                    for (int i = 1; i <= columns; i++) {
                        row.add(String.valueOf(result.getObject(i)));
                    }
                    rows.add(String.join(" ", row));
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return rows;
        }).join();
    }

    private List<String> withNames(List<String> rows) {
        return rows.stream().map(row -> row.replace(alice.toString(), "alice").replace(bob.toString(), "bob")).toList();
    }

    @Test
    void keepsTheMatchWithItsPlayersAndStats() {
        recorder.record(match("tdm")).join();

        assertEquals(List.of("1 team-deathmatch tdm arena-1 " + START.toEpochMilli() + " " + START.plusSeconds(90).toEpochMilli()),
                rows("SELECT id, game, mode, arena, started_at, ended_at FROM matches"));
        assertEquals(List.of("1 alice red 1 0", "1 bob blue 2 1"),
                withNames(rows("SELECT match_id, player, team, place, left_match FROM match_players ORDER BY place")));
        assertEquals(List.of("alice kills 3", "bob kills 1"),
                withNames(rows("SELECT player, stat, amount FROM match_stats ORDER BY amount DESC")));
    }

    @Test
    void totalsAddUpPerGameAndMode() {
        recorder.record(match("tdm")).join();
        recorder.record(match("tdm")).join();
        recorder.record(match("tdm-big")).join();

        assertEquals(List.of(
                        "alice tdm 2 2 2 2 0 180", "alice tdm-big 1 1 1 1 0 90",
                        "bob tdm 2 0 2 4 2 180", "bob tdm-big 1 0 1 2 1 90"),
                withNames(rows("SELECT player, mode, played, wins, placed, place_sum, left_matches, seconds_played"
                        + " FROM player_totals ORDER BY played DESC, mode")).stream().sorted().toList());
        assertEquals(List.of("alice 9", "bob 3"), withNames(rows(
                "SELECT player, SUM(amount) FROM player_stat_totals WHERE game = 'team-deathmatch' AND stat = 'kills'"
                        + " GROUP BY player ORDER BY 2 DESC")));
    }

    @Test
    void aParticipantWithoutAPlaceIsPlayedButNotPlaced() {
        var record = new MatchRecord("build-battle", "build-battle", "build", START, START.plusSeconds(30),
                new MatchResult(List.of(new Placement(1, List.of(alice)))),
                List.of(new Entry(alice, Optional.empty(), false, Map.of()), new Entry(bob, Optional.empty(), true, Map.of())));

        recorder.record(record).join();

        assertEquals(List.of("alice 1 1 1 1", "bob 1 0 0 0"),
                withNames(rows("SELECT player, played, wins, placed, place_sum FROM player_totals")).stream().sorted().toList());
        assertEquals(List.of("alice null 1", "bob null null"),
                withNames(rows("SELECT player, team, place FROM match_players")).stream().sorted().toList());
    }
}
