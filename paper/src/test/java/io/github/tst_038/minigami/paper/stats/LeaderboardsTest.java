package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.stat.Period;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.stats.Leaderboards.Entry;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    /** A Wednesday: its week started on Monday the 23rd, its month on the 1st. */
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);

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
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "Test storage", LOGGER);
        profiles = new PlayerProfiles(storage, serverThread::add, Clock.systemUTC(), LOGGER);
        recorder = new StatsRecorder(storage, profiles, TODAY, LOGGER);
        leaderboards = new Leaderboards(storage, serverThread::add, LOGGER, TODAY, id -> Optional.empty(), 2);
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
    private void play(Optional<String> variant, UUID winner, Map<UUID, Long> kills) {
        play(START, variant, winner, kills);
    }

    private void play(Instant start, Optional<String> variant, UUID winner, Map<UUID, Long> kills) {
        var players = List.of(alice, bob, carol);
        var rest = players.stream().filter(id -> !id.equals(winner)).toList();
        recorder.record(new MatchRecord("team-deathmatch", "tdm", variant, "arena-1", start, start.plusSeconds(60),
                new MatchResult(List.of(new Placement(1, List.of(winner)), new Placement(2, rest))),
                players.stream().map(id -> new MatchRecord.Entry(id, Optional.empty(), false,
                        kills.containsKey(id) ? Map.of(KILLS, kills.get(id)) : Map.<Stat, Long>of())).toList())).join();
    }

    @Test
    void boardsShowTheBestPerGameAndPerVariantOnlyAfterARefresh() {
        profiles.join(alice, "Alice");
        profiles.join(bob, "Bob");
        play(Optional.empty(), alice, Map.of(alice, 2L, bob, 5L, carol, 1L));
        play(Optional.empty(), alice, Map.of(bob, 1L));
        play(Optional.of("big"), bob, Map.of(carol, 9L));
        settle();
        assertTrue(leaderboards.top("tdm", Optional.empty(), KILLS.id()).isEmpty(), "not refreshed yet");
        assertTrue(leaderboards.games().isEmpty());

        leaderboards.refresh();
        settle();

        assertEquals(List.of(new Entry(carol, Optional.empty(), 10), new Entry(bob, Optional.of("Bob"), 6)),
                leaderboards.top("tdm", Optional.empty(), KILLS.id()), "the variants added up, cut to the size; carol never joined, so no name");
        assertEquals(List.of(new Entry(alice, Optional.of("Alice"), 2), new Entry(bob, Optional.of("Bob"), 1)),
                leaderboards.top("tdm", Optional.empty(), Stat.WINS));
        assertEquals(List.of(new Entry(carol, Optional.empty(), 9)), leaderboards.top("tdm", Optional.of("big"), KILLS.id()));
        assertEquals(List.of(new Entry(bob, Optional.of("Bob"), 1)), leaderboards.top("tdm", Optional.of("big"), Stat.WINS));
        assertEquals(List.of(3L, 3L), leaderboards.top("tdm", Optional.empty(), Stat.PLAYED).stream().map(Entry::amount).toList(),
                "everyone played all three matches, variants added up");
        assertEquals(List.of(1L, 1L), leaderboards.top("tdm", Optional.of("big"), Stat.PLAYED).stream().map(Entry::amount).toList());
        assertEquals(Set.of(Stat.WINS, Stat.PLAYED, KILLS.id()), leaderboards.stats("tdm"));
        assertEquals(Set.of("tdm"), leaderboards.games());

        play(Optional.empty(), bob, Map.of(alice, 10L));
        settle();
        assertEquals(new Entry(carol, Optional.empty(), 10), leaderboards.top("tdm", Optional.empty(), KILLS.id()).getFirst(), "cached until the next refresh");
        leaderboards.refresh();
        settle();
        assertEquals(new Entry(alice, Optional.of("Alice"), 12), leaderboards.top("tdm", Optional.empty(), KILLS.id()).getFirst());
    }

    @Test
    void weeklyAndMonthlyBoardsCountOnlyTheDaysOfThePeriod() {
        profiles.join(alice, "Alice");
        profiles.join(bob, "Bob");
        play(Instant.parse("2026-10-31T23:00:00Z"), Optional.empty(), alice, Map.of(alice, 100L));
        play(Instant.parse("2026-11-01T00:30:00Z"), Optional.empty(), alice, Map.of(alice, 10L, bob, 1L));
        play(Instant.parse("2026-11-22T23:00:00Z"), Optional.empty(), bob, Map.of(alice, 20L));
        play(Instant.parse("2026-11-23T00:30:00Z"), Optional.of("big"), bob, Map.of(bob, 5L, alice, 4L));
        play(Instant.parse("2026-11-25T11:00:00Z"), Optional.empty(), bob, Map.of(bob, 2L));
        leaderboards.refresh();
        settle();

        assertEquals(List.of(new Entry(alice, Optional.of("Alice"), 134), new Entry(bob, Optional.of("Bob"), 8)),
                leaderboards.top("tdm", Optional.empty(), KILLS.id()));
        assertEquals(leaderboards.top("tdm", Optional.empty(), KILLS.id()), leaderboards.top(Period.ALL_TIME, "tdm", Optional.empty(), KILLS.id()));
        assertEquals(List.of(new Entry(alice, Optional.of("Alice"), 34), new Entry(bob, Optional.of("Bob"), 8)),
                leaderboards.top(Period.MONTH, "tdm", Optional.empty(), KILLS.id()), "from the 1st on, so not the 31st");
        assertEquals(List.of(new Entry(bob, Optional.of("Bob"), 7), new Entry(alice, Optional.of("Alice"), 4)),
                leaderboards.top(Period.WEEK, "tdm", Optional.empty(), KILLS.id()), "from Monday on, so not the 22nd, the variants added up");
        assertEquals(List.of(new Entry(bob, Optional.of("Bob"), 5), new Entry(alice, Optional.of("Alice"), 4)),
                leaderboards.top(Period.WEEK, "tdm", Optional.of("big"), KILLS.id()));
        assertTrue(leaderboards.top(Period.WEEK, "tdm", Optional.of("small"), KILLS.id()).isEmpty());
    }

    @Test
    void weeklyAndMonthlyBoardsHaveWinsAndPlayedFromTheDays() {
        profiles.join(alice, "Alice");
        profiles.join(bob, "Bob");
        play(Instant.parse("2026-11-16T12:00:00Z"), Optional.empty(), alice, Map.of());
        play(Instant.parse("2026-11-16T13:00:00Z"), Optional.empty(), alice, Map.of());
        play(Instant.parse("2026-11-24T12:00:00Z"), Optional.of("big"), bob, Map.of());
        leaderboards.refresh();
        settle();

        assertEquals(List.of(new Entry(alice, Optional.of("Alice"), 2), new Entry(bob, Optional.of("Bob"), 1)),
                leaderboards.top(Period.MONTH, "tdm", Optional.empty(), Stat.WINS));
        assertEquals(List.of(new Entry(bob, Optional.of("Bob"), 1)), leaderboards.top(Period.WEEK, "tdm", Optional.empty(), Stat.WINS),
                "last week's matches count in the month but not the week");
        assertEquals(List.of(1L, 1L), leaderboards.top(Period.WEEK, "tdm", Optional.empty(), Stat.PLAYED).stream().map(Entry::amount).toList());
        assertEquals(List.of(3L, 3L), leaderboards.top(Period.MONTH, "tdm", Optional.empty(), Stat.PLAYED).stream().map(Entry::amount).toList());
        assertEquals(List.of(1L, 1L), leaderboards.top(Period.WEEK, "tdm", Optional.of("big"), Stat.PLAYED).stream().map(Entry::amount).toList());
        assertEquals(Set.of(Stat.WINS, Stat.PLAYED), leaderboards.stats("tdm"));
    }
}
