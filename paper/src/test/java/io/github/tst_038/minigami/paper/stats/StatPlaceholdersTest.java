package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.storage.Migrations;
import io.github.tst_038.minigami.paper.storage.SqliteStorage;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.nio.file.Path;
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

class StatPlaceholdersTest {

    private static final Logger LOGGER = Logger.getLogger(StatPlaceholdersTest.class.getName());
    private static final Instant START = Instant.parse("2026-10-28T20:00:00Z");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final List<Runnable> serverThread = new ArrayList<>();
    @TempDir
    private Path folder;
    private Storage storage;
    private PlayerProfiles profiles;
    private Leaderboards leaderboards;
    private StatPlaceholders placeholders;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), LOGGER);
        profiles = new PlayerProfiles(storage, serverThread::add, Clock.systemUTC(), LOGGER);
        leaderboards = new Leaderboards(storage, serverThread::add, LOGGER, id -> Optional.empty(), 10);
        placeholders = new StatPlaceholders(leaderboards, profiles);
        profiles.join(alice, "Alice");
        profiles.join(bob, "Bob");
        var recorder = new StatsRecorder(storage, profiles, LOGGER);
        play(recorder, Optional.empty(), Map.of(alice, 3L, bob, 1L));
        play(recorder, Optional.of("duos"), Map.of(alice, 2L, bob, 4L));
        leaderboards.refresh();
        profiles.load(alice);
        storage.submit(connection -> null).join();
        List.copyOf(serverThread).forEach(Runnable::run);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    /** Alice wins; each player's kills. */
    private void play(StatsRecorder recorder, Optional<String> variant, Map<UUID, Long> kills) {
        recorder.record(new MatchRecord("last-standing", "spleef", variant, "arena-1", START, START.plusSeconds(60),
                new MatchResult(List.of(new Placement(1, List.of(alice)), new Placement(2, List.of(bob)))),
                List.of(alice, bob).stream().map(id -> new MatchRecord.Entry(id, Optional.empty(), false,
                        Map.of(new Stat("kills"), kills.get(id)))).toList())).join();
    }

    @Test
    void placesOnABoardOfTheGameOrAVariant() {
        assertEquals("Alice", placeholders.resolve(Optional.empty(), "top_spleef_kills_1_name"));
        assertEquals("5", placeholders.resolve(Optional.empty(), "top_spleef_kills_1_value"));
        assertEquals("Bob", placeholders.resolve(Optional.empty(), "top_spleef_duos_kills_1_name"));
        assertEquals("2", placeholders.resolve(Optional.empty(), "top_spleef_wins_1_value"));
        assertEquals("", placeholders.resolve(Optional.empty(), "top_spleef_kills_3_name"));
        assertEquals("", placeholders.resolve(Optional.empty(), "top_tnt-run_kills_1_name"));
    }

    @Test
    void theViewersOwnStats() {
        assertEquals("5", placeholders.resolve(Optional.of(alice), "stat_spleef_kills"));
        assertEquals("2", placeholders.resolve(Optional.of(alice), "stat_spleef_duos_kills"));
        assertEquals("2", placeholders.resolve(Optional.of(alice), "stat_spleef_played"));
        assertEquals("1", placeholders.resolve(Optional.of(alice), "stat_spleef_duos_wins"));
        assertEquals("", placeholders.resolve(Optional.of(UUID.randomUUID()), "stat_spleef_kills"), "not online or still loading");
        assertEquals("", placeholders.resolve(Optional.empty(), "stat_spleef_kills"));
    }

    @Test
    void whatIsNotOursStaysAsTyped() {
        assertNull(placeholders.resolve(Optional.empty(), "top_spleef_kills_first_name"));
        assertNull(placeholders.resolve(Optional.empty(), "top_spleef_kills_0_name"));
        assertNull(placeholders.resolve(Optional.empty(), "top_spleef_kills_1_uuid"));
        assertNull(placeholders.resolve(Optional.empty(), "kills"));
        assertNull(placeholders.resolve(Optional.empty(), "stat_spleef"));
    }
}
