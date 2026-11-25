package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.stat.Period;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

class PluginStatsTest {

    private static final Stat CAPTURES = new Stat("captures");
    private static final Stat SECONDS = new Stat("seconds-on-hill");
    private static final Logger LOGGER = Logger.getLogger(PluginStatsTest.class.getName());
    private static final Instant NOW = Instant.parse("2026-10-28T20:00:00Z");

    private final UUID alice = UUID.randomUUID();
    @TempDir
    private Path folder;
    private Storage storage;
    private PlayerProfiles profiles;
    private PluginStats stats;
    private Leaderboards leaderboards;
    private final List<Runnable> serverTasks = new ArrayList<>();

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "Test storage", LOGGER);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // Everything handed to the server thread runs at once.
        profiles = new PlayerProfiles(storage, Runnable::run, clock, LOGGER);
        leaderboards = new Leaderboards(storage, Runnable::run, LOGGER, clock, id -> Optional.empty(), () -> 5);
        stats = new PluginStats(storage, profiles, leaderboards, serverTasks::add, clock, LOGGER);
        profiles.join(alice, "Alice");
        settle();
    }

    @AfterEach
    void close() {
        storage.close();
    }

    private void settle() {
        // The flush the server thread would run after the tick.
        serverTasks.forEach(Runnable::run);
        serverTasks.clear();
        for (int round = 0; round < 3; round++) {
            storage.submit(connection -> null).join();
        }
    }

    private List<String> rows(String sql) {
        return storage.submit(connection -> {
            var rows = new ArrayList<String>();
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                while (result.next()) {
                    var row = new ArrayList<String>();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                        row.add(String.valueOf(result.getObject(i)));
                    }
                    rows.add(String.join(" ", row).replace(alice.toString(), "alice"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return rows;
        }).join();
    }

    @Test
    void addsToTheTotalAndTodaysRowAndShowsInTheProfile() {
        stats.add("koth", Map.of(alice, Map.of(CAPTURES, 2L)));
        stats.add("koth", Map.of(alice, Map.of(CAPTURES, 3L)));
        settle();

        assertEquals(List.of("alice koth  captures 5"), rows("SELECT player, game, variant, stat, amount FROM player_stat_totals"));
        assertEquals(List.of("alice koth  captures " + LocalDate.of(2026, 10, 28).toEpochDay() + " 5"),
                rows("SELECT player, game, variant, stat, day, amount FROM player_daily_stats"));
        assertEquals(5, profiles.of(alice).orElseThrow().game("koth").stat(CAPTURES));
        assertEquals(5, profiles.of(alice, Period.WEEK).orElseThrow().game("koth").stat(CAPTURES));
    }

    @Test
    void addsOfOneTickQueueOneFlushThatRefreshesTheBoards() {
        var bob = UUID.randomUUID();
        stats.add("koth", Map.of(alice, Map.of(CAPTURES, 2L)));
        stats.add("koth", Map.of(bob, Map.of(CAPTURES, 7L)));

        assertEquals(1, serverTasks.size());
        assertTrue(leaderboards.games().isEmpty());

        settle();

        assertTrue(leaderboards.games().contains("koth"));
        assertEquals(List.of(7L, 2L), leaderboards.top("koth", Optional.empty(), "captures").stream().map(Leaderboards.Entry::amount).toList());

        stats.add("koth", Map.of(alice, Map.of(CAPTURES, 1L)));
        assertEquals(1, serverTasks.size());
    }

    @Test
    void negativeAmountsTakeAway() {
        stats.add("koth", Map.of(alice, Map.of(SECONDS, 10L)));
        stats.add("koth", Map.of(alice, Map.of(SECONDS, -4L)));
        settle();

        assertEquals(6, profiles.of(alice).orElseThrow().game("koth").stat(SECONDS));
    }

    @Test
    void zeroAmountsAreSkipped() {
        stats.add("koth", Map.of(alice, Map.of(CAPTURES, 0L)));
        settle();

        assertEquals(List.of(), rows("SELECT * FROM player_stat_totals"));
        assertEquals(List.of(), rows("SELECT * FROM player_daily_stats"));
    }

    @Test
    void anInvalidGameIdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> stats.add("King Of The Hill", Map.of(alice, Map.of(CAPTURES, 1L))));
        assertThrows(IllegalArgumentException.class, () -> stats.add("", Map.of(alice, Map.of(CAPTURES, 1L))));
    }

    @Test
    void theStatsMiniGamiKeepsItselfAreRefusedAndNothingIsStored() {
        assertThrows(IllegalArgumentException.class, () -> stats.add("koth", Map.of(alice, Map.of(new Stat(Stat.PLAYED), 1L))));
        assertThrows(IllegalArgumentException.class, () -> stats.add("koth", Map.of(alice, Map.of(new Stat(Stat.WINS), 1L))));
        settle();

        assertEquals(List.of(), rows("SELECT * FROM player_stat_totals"));
    }
}
