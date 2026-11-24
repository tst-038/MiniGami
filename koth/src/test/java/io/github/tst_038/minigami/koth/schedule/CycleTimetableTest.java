package io.github.tst_038.minigami.koth.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CycleTimetableTest {

    private static LocalDateTime at(int day, int hour, int minute, int second) {
        return LocalDateTime.of(2026, 3, day, hour, minute, second);
    }

    private static Window window(int day, int hour, int minute, int lengthMinutes) {
        var start = at(day, hour, minute, 0);
        return new Window(start, start.plusMinutes(lengthMinutes));
    }

    private final CycleTimetable fifteenOnFiveOff = new CycleTimetable(Duration.ofMinutes(15), Duration.ofMinutes(5));

    @Test
    void windowsLineUpWithMidnightWhenThePeriodDividesTheDay() {
        assertEquals(Optional.of(window(2, 0, 0, 15)), fifteenOnFiveOff.current(at(2, 0, 0, 0)));
        assertEquals(Optional.of(window(2, 0, 0, 15)), fifteenOnFiveOff.current(at(2, 0, 14, 59)));
        assertEquals(Optional.of(window(2, 0, 20, 15)), fifteenOnFiveOff.current(at(2, 0, 20, 0)));
        assertEquals(Optional.of(window(2, 23, 40, 15)), fifteenOnFiveOff.current(at(2, 23, 50, 0)));
    }

    @Test
    void nothingRunsDuringTheBreak() {
        assertEquals(Optional.empty(), fifteenOnFiveOff.current(at(2, 0, 15, 0)));
        assertEquals(Optional.empty(), fifteenOnFiveOff.current(at(2, 0, 19, 59)));
    }

    @Test
    void nextIsTheStartOfTheFollowingCycle() {
        assertEquals(Optional.of(window(2, 0, 20, 15)), fifteenOnFiveOff.next(at(2, 0, 10, 0)));
        assertEquals(Optional.of(window(2, 0, 20, 15)), fifteenOnFiveOff.next(at(2, 0, 16, 0)));
        assertEquals(Optional.of(window(2, 0, 20, 15)), fifteenOnFiveOff.next(at(2, 0, 0, 0)));
        assertEquals(Optional.of(window(3, 0, 0, 15)), fifteenOnFiveOff.next(at(2, 23, 45, 0)));
    }

    @Test
    void theSameWindowsComeFromAnyTimeInsideThem() {
        var first = fifteenOnFiveOff.current(at(5, 13, 40, 0));
        var last = fifteenOnFiveOff.current(at(5, 13, 54, 59));

        assertEquals(first, last);
        assertEquals(Optional.of(window(5, 13, 40, 15)), first);
    }

    @Test
    void aPeriodThatDoesNotDivideTheDayStillRepeatsExactly() {
        var timetable = new CycleTimetable(Duration.ofMinutes(25), Duration.ofMinutes(10));
        var window = timetable.current(at(5, 13, 0, 0)).orElseThrow();

        assertEquals(window.start().plusMinutes(35), timetable.next(window.start()).orElseThrow().start());
        assertEquals(Optional.of(new Window(window.start().plusMinutes(35), window.end().plusMinutes(35))),
                timetable.current(window.start().plusMinutes(35)));
        assertEquals(Optional.empty(), timetable.current(window.end()));
    }

    @Test
    void theLengthIsTheActivePart() {
        assertEquals(Duration.ofMinutes(15), fifteenOnFiveOff.length());
    }

    @Test
    void aCycleNeedsAMinuteOfEach() {
        assertThrows(IllegalArgumentException.class, () -> new CycleTimetable(Duration.ZERO, Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> new CycleTimetable(Duration.ofMinutes(5), Duration.ofSeconds(30)));
    }
}
