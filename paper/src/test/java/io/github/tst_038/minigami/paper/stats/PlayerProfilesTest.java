package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.match.MatchEndEvent;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchRecord.Entry;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.storage.Migrations;
import io.github.tst_038.minigami.paper.storage.SqliteStorage;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerProfilesTest {

    private static final Stat KILLS = new Stat("kills");
    private static final Logger LOGGER = Logger.getLogger(PlayerProfilesTest.class.getName());
    private static final Instant START = Instant.parse("2026-10-28T20:00:00Z");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    /** Stands in for the server thread: what the profiles hand over runs when the test says so. */
    private final List<Runnable> serverThread = new ArrayList<>();
    @TempDir
    private Path folder;
    private Storage storage;
    private PlayerProfiles profiles;
    private StatsRecorder recorder;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), LOGGER);
        profiles = new PlayerProfiles(storage, serverThread::add, Clock.systemUTC(), LOGGER);
        recorder = new StatsRecorder(storage, profiles, LOGGER);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    /** Waits for everything submitted so far, then runs what was handed to the server thread. */
    private void settle() {
        storage.submit(connection -> null).join();
        List.copyOf(serverThread).forEach(Runnable::run);
        serverThread.clear();
    }

    private void play(String mode, int alicePlace, long aliceKills) {
        var bobPlace = 3 - alicePlace;
        recorder.record(new MatchRecord("team-deathmatch", mode, "arena-1", START, START.plusSeconds(60),
                new MatchResult(List.of(new Placement(alicePlace, List.of(alice)), new Placement(bobPlace, List.of(bob)))),
                List.of(new Entry(alice, Optional.of("red"), false, Map.of(KILLS, aliceKills)),
                        new Entry(bob, Optional.of("blue"), false, Map.of())))).join();
    }

    @Test
    void aProfileHasTheTotalsPerModeAndPerGame() {
        play("tdm", 1, 3);
        play("tdm", 2, 1);
        play("tdm-big", 1, 5);

        profiles.load(alice);
        assertTrue(profiles.of(alice).isEmpty(), "still loading");
        settle();

        var profile = profiles.of(alice).orElseThrow();
        var tdm = profile.mode("team-deathmatch", "tdm");
        assertEquals(2, tdm.played());
        assertEquals(1, tdm.wins());
        assertEquals(OptionalDouble.of(1.5), tdm.averagePlace());
        assertEquals(4, tdm.stat(KILLS));
        var game = profile.game("team-deathmatch");
        assertEquals(3, game.played());
        assertEquals(2, game.wins());
        assertEquals(9, game.stat(KILLS));
        assertEquals(Duration.ofMinutes(3), game.timePlayed());
        assertEquals(Set.of("team-deathmatch"), profile.games());
        assertEquals(Totals.NONE, profile.game("build-battle"));
    }

    @Test
    void aRecordedMatchReloadsTheProfilesOfThoseOnline() {
        profiles.load(alice);
        settle();
        assertEquals(0, profiles.of(alice).orElseThrow().game("team-deathmatch").played());

        recorder.onMatchEnd(new MatchEndEvent(new MatchRecord("team-deathmatch", "tdm", "arena-1",
                START, START.plusSeconds(60), new MatchResult(List.of(new Placement(1, List.of(alice, bob)))),
                List.of(new Entry(alice, Optional.empty(), false, Map.of()), new Entry(bob, Optional.empty(), false, Map.of())))));
        settle();

        assertEquals(1, profiles.of(alice).orElseThrow().game("team-deathmatch").played());
        assertTrue(profiles.of(bob).isEmpty(), "bob isn't online");
    }

    @Test
    void quittingDropsTheProfileAndALateLoadIsThrownAway() {
        profiles.load(alice);
        settle();
        profiles.load(alice);

        profiles.forget(alice);
        settle();

        assertTrue(profiles.of(alice).isEmpty());
    }

    @Test
    void anyoneWhoJoinedCanBeFoundByNameInAnyCase() {
        play("tdm", 1, 3);
        profiles.join(alice, "Alice");
        profiles.forget(alice);
        settle();

        var lookup = profiles.find("ALICE");
        var nobody = profiles.find("bob");
        settle();

        var found = lookup.join();
        assertEquals(alice, found.orElseThrow().id());
        assertEquals("Alice", found.get().name());
        assertEquals(3, found.get().profile().mode("team-deathmatch", "tdm").stat(KILLS));
        assertTrue(nobody.join().isEmpty(), "bob never joined");
    }
}
