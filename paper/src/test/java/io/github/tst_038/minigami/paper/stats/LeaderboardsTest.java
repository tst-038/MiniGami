package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.stats.Leaderboards.Entry;
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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeaderboardsTest {

    private static final Stat KILLS = new Stat("kills");
    private static final Logger LOGGER = Logger.getLogger(LeaderboardsTest.class.getName());
    private static final Instant START = Instant.parse("2026-10-28T20:00:00Z");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final List<Runnable> serverThread = new ArrayList<>();
    @TempDir
    private Path folder;
    private Storage storage;
    private PlayerProfiles profiles;
    private StatsRecorder recorder;
    private Leaderboards leaderboards;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), LOGGER);
        profiles = new PlayerProfiles(storage, serverThread::add, Clock.systemUTC(), LOGGER);
        recorder = new StatsRecorder(storage, profiles, LOGGER);
        leaderboards = new Leaderboards(storage, serverThread::add, LOGGER, id -> Optional.empty(), 2);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    private void settle() {
        storage.submit(connection -> null).join();
        List.copyOf(serverThread).forEach(Runnable::run);
        serverThread.clear();
    }

    /** The winner first; each player's kills. */
    private void play(String mode, UUID winner, Map<UUID, Long> kills) {
        var players = List.of(alice, bob, carol);
        var rest = players.stream().filter(id -> !id.equals(winner)).toList();
        recorder.record(new MatchRecord("team-deathmatch", mode, "arena-1", START, START.plusSeconds(60),
                new MatchResult(List.of(new Placement(1, List.of(winner)), new Placement(2, rest))),
                players.stream().map(id -> new MatchRecord.Entry(id, Optional.empty(), false,
                        kills.containsKey(id) ? Map.of(KILLS, kills.get(id)) : Map.<Stat, Long>of())).toList())).join();
    }

    @Test
    void boardsShowTheBestPerModeAndStatOnlyAfterARefresh() {
        profiles.join(alice, "Alice");
        profiles.join(bob, "Bob");
        play("tdm", alice, Map.of(alice, 2L, bob, 5L, carol, 1L));
        play("tdm", alice, Map.of(bob, 1L));
        play("tdm-big", bob, Map.of(carol, 9L));
        settle();
        assertTrue(leaderboards.top("team-deathmatch", "tdm", KILLS.id()).isEmpty(), "not refreshed yet");

        leaderboards.refresh();
        settle();

        assertEquals(List.of(new Entry(bob, Optional.of("Bob"), 6), new Entry(alice, Optional.of("Alice"), 2)), leaderboards.top("team-deathmatch", "tdm", KILLS.id()), "cut to the size");
        assertEquals(List.of(new Entry(alice, Optional.of("Alice"), 2)), leaderboards.top("team-deathmatch", "tdm", Leaderboards.WINS));
        assertEquals(List.of(new Entry(carol, Optional.empty(), 9)), leaderboards.top("team-deathmatch", "tdm-big", KILLS.id()), "never joined, so no name");
        assertEquals(Set.of(Leaderboards.WINS, KILLS.id()), leaderboards.stats("team-deathmatch", "tdm"));

        play("tdm", bob, Map.of(alice, 10L));
        settle();
        assertEquals(new Entry(bob, Optional.of("Bob"), 6), leaderboards.top("team-deathmatch", "tdm", KILLS.id()).getFirst(), "cached until the next refresh");
        leaderboards.refresh();
        settle();
        assertEquals(new Entry(alice, Optional.of("Alice"), 12), leaderboards.top("team-deathmatch", "tdm", KILLS.id()).getFirst());
    }
}
