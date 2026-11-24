package io.github.tst_038.minigami.koth.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.core.schedule.Cron;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CronTimetableTest {

    private static LocalDateTime at(int day, int hour, int minute, int second) {
        return LocalDateTime.of(2026, 3, day, hour, minute, second);
    }

    private static Window window(int day, int hour, int minute, int lengthMinutes) {
        var start = at(day, hour, minute, 0);
        return new Window(start, start.plusMinutes(lengthMinutes));
    }

    private static CronTimetable daily(int length, String... lines) {
        return new CronTimetable(Arrays.stream(lines).map(Cron::parse).toList(), Duration.ofMinutes(length));
    }

    @Test
    void theWindowIsCurrentFromItsStartUntilItsEnd() {
        var timetable = daily(60, "0 20 * * *");

        assertEquals(Optional.empty(), timetable.current(at(2, 19, 59, 59)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 30, 15)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 59, 59)));
    }

    @Test
    void theEndIsExclusive() {
        assertEquals(Optional.empty(), daily(60, "0 20 * * *").current(at(2, 21, 0, 0)));
    }

    @Test
    void nextIsTheFirstStartAfterNow() {
        var timetable = daily(60, "0 20 * * *");

        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.next(at(2, 19, 0, 0)));
        assertEquals(Optional.of(window(3, 20, 0, 60)), timetable.next(at(2, 20, 0, 0)));
        assertEquals(Optional.of(window(3, 20, 0, 60)), timetable.next(at(2, 20, 30, 0)));
    }

    @Test
    void severalLinesTakeTheEarliest() {
        var timetable = daily(30, "0 20 * * *", "0 18 * * *");

        assertEquals(Optional.of(window(2, 18, 0, 30)), timetable.next(at(2, 17, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 30)), timetable.next(at(2, 19, 0, 0)));
        assertEquals(Optional.of(window(2, 18, 0, 30)), timetable.current(at(2, 18, 10, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 30)), timetable.current(at(2, 20, 10, 0)));
    }

    @Test
    void aStartInTheLastMinuteOfAWindowKeepsTheEarlierWindowCurrentUntilItEnds() {
        var timetable = daily(60, "0 20 * * *", "59 20 * * *");

        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 59, 30)));
        assertEquals(Optional.of(window(2, 20, 59, 60)), timetable.current(at(2, 21, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 59, 60)), timetable.next(at(2, 20, 30, 0)));
    }

    @Test
    void windowsLongerThanTheGapBetweenStartsAreTakenInTurn() {
        var timetable = daily(90, "0 20 * * *", "0 21 * * *");

        assertEquals(Optional.of(window(2, 20, 0, 90)), timetable.current(at(2, 21, 15, 0)));
        assertEquals(Optional.of(window(2, 21, 0, 90)), timetable.current(at(2, 21, 30, 0)));
    }

    @Test
    void aWindowCanRunPastMidnight() {
        var timetable = daily(120, "0 23 * * *");

        assertEquals(Optional.of(window(2, 23, 0, 120)), timetable.current(at(3, 0, 30, 0)));
    }

    @Test
    void anImpossibleLineNeverStarts() {
        var timetable = daily(60, "0 0 30 2 *");

        assertEquals(Optional.empty(), timetable.current(at(2, 12, 0, 0)));
        assertEquals(Optional.empty(), timetable.next(at(2, 12, 0, 0)));
    }

    @Test
    void aTimetableNeedsAStartAndAMinute() {
        assertThrows(IllegalArgumentException.class, () -> new CronTimetable(List.of(), Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> daily(0, "0 20 * * *"));
        assertThrows(IllegalArgumentException.class,
                () -> new CronTimetable(List.of(Cron.parse("0 20 * * *")), Duration.ofSeconds(59)));
    }
}
