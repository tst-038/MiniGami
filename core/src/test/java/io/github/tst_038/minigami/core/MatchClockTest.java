package io.github.tst_038.minigami.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchClockTest {

    private final MatchClock clock = new MatchClock();
    private final List<Long> runs = new ArrayList<>();

    private Runnable record() {
        return () -> runs.add(clock.elapsedTicks());
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) {
            clock.tick();
        }
    }

    @Test
    void countsElapsedTicks() {
        tick(3);

        assertEquals(3, clock.elapsedTicks());
    }

    @Test
    void scheduledTaskRunsOnceAfterItsDelay() {
        clock.schedule(5, record());

        tick(10);

        assertEquals(List.of(5L), runs);
    }

    @Test
    void repeatingTaskRunsEveryPeriod() {
        clock.repeat(3, record());

        tick(10);

        assertEquals(List.of(3L, 6L, 9L), runs);
    }

    @Test
    void closedTaskDoesNotRun() {
        var task = clock.schedule(2, record());

        task.close();
        tick(5);

        assertTrue(runs.isEmpty());
        assertTrue(task.isCancelled());
    }

    @Test
    void repeatingTaskCanCancelItself() {
        var task = new MatchClock.Task[1];
        task[0] = clock.repeat(1, () -> {
            runs.add(clock.elapsedTicks());
            if (runs.size() == 2) {
                task[0].close();
            }
        });

        tick(5);

        assertEquals(List.of(1L, 2L), runs);
    }

    @Test
    void taskClosedByEarlierTaskInSameTickDoesNotRun() {
        var later = new MatchClock.Task[1];
        clock.schedule(1, () -> later[0].close());
        later[0] = clock.schedule(1, record());

        tick(2);

        assertTrue(runs.isEmpty());
    }

    @Test
    void taskScheduledDuringTickRunsOnALaterTick() {
        clock.schedule(1, () -> clock.schedule(1, record()));

        tick(3);

        assertEquals(List.of(2L), runs);
    }

    @Test
    void pausedClockStandsStill() {
        clock.schedule(2, record());
        tick(1);

        clock.pause();
        tick(5);

        assertEquals(1, clock.elapsedTicks());
        assertTrue(runs.isEmpty());
        assertTrue(clock.isPaused());
    }

    @Test
    void resumedCountdownContinuesWhereItLeftOff() {
        clock.schedule(3, record());
        tick(2);
        clock.pause();
        tick(10);

        clock.resume();
        tick(1);

        assertEquals(List.of(3L), runs);
    }

    @Test
    void closingTheOwningScopeCancelsTheTask() {
        var scope = new Scope();
        scope.register(clock.repeat(1, record()));
        tick(2);

        scope.close();
        tick(2);

        assertEquals(List.of(1L, 2L), runs);
    }

    @Test
    void tasksDueOnTheSameTickRunInScheduleOrder() {
        var order = new ArrayList<String>();
        clock.schedule(3, () -> order.add("a"));
        tick(1);
        clock.schedule(2, () -> order.add("b"));
        clock.schedule(2, () -> order.add("c"));

        tick(2);

        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    void failingTaskDoesNotStopTheOthers() {
        var failing = clock.repeat(1, () -> { throw new IllegalArgumentException("boom"); });
        clock.schedule(1, record());

        var thrown = assertThrows(IllegalStateException.class, clock::tick);

        assertEquals(List.of(1L), runs);
        assertEquals("boom", thrown.getCause().getMessage());
        assertFalse(failing.isCancelled());
    }

    @Test
    void laterFailuresInTheSameTickAreAttachedAsSuppressed() {
        clock.schedule(1, () -> { throw new IllegalArgumentException("first"); });
        clock.schedule(1, () -> { throw new IllegalArgumentException("second"); });

        var thrown = assertThrows(IllegalStateException.class, clock::tick);

        assertEquals("first", thrown.getCause().getMessage());
        assertEquals("second", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void rejectsNonPositiveDelayAndPeriod() {
        assertThrows(IllegalArgumentException.class, () -> clock.schedule(0, record()));
        assertThrows(IllegalArgumentException.class, () -> clock.repeat(-1, record()));
    }
}
