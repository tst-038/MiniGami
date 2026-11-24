package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DayRolloverTest {

    private static Clock at(String instant, ZoneId zone) {
        return Clock.fixed(Instant.parse(instant), zone);
    }

    /** A clock that moves when the test says so. */
    private static final class Moving extends Clock {

        private Instant now;

        Moving(String instant) {
            now = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void waitsUntilTheNextMidnightOfTheZone() {
        assertEquals(Duration.ofHours(4), DayRollover.untilNextDay(at("2026-10-28T20:00:00Z", ZoneOffset.UTC)));
        assertEquals(Duration.ofDays(1), DayRollover.untilNextDay(at("2026-10-28T00:00:00Z", ZoneOffset.UTC)));
        assertEquals(Duration.ofSeconds(1), DayRollover.untilNextDay(at("2026-10-28T23:59:59Z", ZoneOffset.UTC)));
        // Midnight in Berlin (UTC+1 in winter) is 23:00 in UTC.
        assertEquals(Duration.ofHours(2), DayRollover.untilNextDay(at("2026-12-01T21:00:00Z", ZoneId.of("Europe/Berlin"))));
    }

    @Test
    void aDayWhenTheClocksChangeIsNotTwentyFourHours() {
        var berlin = ZoneId.of("Europe/Berlin");
        // The clocks go back at 03:00 on the 25th of October 2026, so that day has 25 hours: from 00:00 (+02:00) it is 25 hours to the 26th.
        assertEquals(Duration.ofHours(25), DayRollover.untilNextDay(at("2026-10-24T22:00:00Z", berlin)));
        // And forward on the 29th of March 2026: 23 hours.
        assertEquals(Duration.ofHours(23), DayRollover.untilNextDay(at("2026-03-28T23:00:00Z", berlin)));
    }

    @Test
    void whenMidnightDoesNotExistTheDayStartsWhenItDoes() {
        // In Havana the clocks go forward at 00:00 on the 8th of March 2026, so that day starts at 01:00.
        assertEquals(Duration.ofHours(1), DayRollover.untilNextDay(at("2026-03-08T04:00:00Z", ZoneId.of("America/Havana"))));
    }

    @Test
    void runsOnceADayAndSchedulesTheNextMidnightEachTime() {
        var clock = new Moving("2026-10-28T20:00:00Z");
        var delays = new ArrayList<Duration>();
        var tasks = new ArrayList<Runnable>();
        var rolled = new ArrayList<String>();
        new DayRollover(clock, (delay, task) -> {
            delays.add(delay);
            tasks.add(task);
            return () -> {
            };
        }, () -> rolled.add(clock.instant().toString())).start();
        assertEquals(List.of(Duration.ofHours(4)), delays);

        clock.now = Instant.parse("2026-10-29T00:00:00Z");
        tasks.get(0).run();

        assertEquals(List.of("2026-10-29T00:00:00Z"), rolled);
        assertEquals(List.of(Duration.ofHours(4), Duration.ofDays(1)), delays);
    }

    @Test
    void aTimerThatGoesOffEarlyChangesNothingAndWaitsForTheRest() {
        var clock = new Moving("2026-10-28T20:00:00Z");
        var delays = new ArrayList<Duration>();
        var tasks = new ArrayList<Runnable>();
        var rolled = new ArrayList<String>();
        new DayRollover(clock, (delay, task) -> {
            delays.add(delay);
            tasks.add(task);
            return () -> {
            };
        }, () -> rolled.add("new day")).start();

        clock.now = Instant.parse("2026-10-28T23:59:59.999Z");
        tasks.get(0).run();

        assertEquals(List.of(), rolled);
        assertEquals(List.of(Duration.ofHours(4), Duration.ofMillis(1)), delays);
    }

    @Test
    void stoppingCancelsTheWaitAndNothingRunsAfter() {
        var clock = new Moving("2026-10-28T20:00:00Z");
        var tasks = new ArrayList<Runnable>();
        var cancelled = new ArrayList<Boolean>();
        var rolled = new ArrayList<String>();
        var rollover = new DayRollover(clock, (delay, task) -> {
            tasks.add(task);
            return () -> cancelled.add(true);
        }, () -> rolled.add("new day"));
        rollover.start();

        rollover.stop();
        // A timer that had already gone off when it was cancelled.
        clock.now = Instant.parse("2026-10-29T00:00:00Z");
        tasks.get(0).run();

        assertEquals(List.of(true), cancelled);
        assertEquals(List.of(), rolled);
        assertEquals(1, tasks.size());
    }
}
