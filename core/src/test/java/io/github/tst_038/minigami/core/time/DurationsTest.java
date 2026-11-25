package io.github.tst_038.minigami.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void zeroAndNegativeLengthsReadZeroSeconds() {
        assertEquals("0s", Durations.format(Duration.ZERO));
        assertEquals("0s", Durations.format(Duration.ofSeconds(-5)));
        assertEquals("0s", Durations.format(Duration.ofMillis(-1)));
    }

    @Test
    void secondsStandAlone() {
        assertEquals("9s", Durations.format(Duration.ofSeconds(9)));
    }

    @Test
    void minutesAndSeconds() {
        assertEquals("4m 9s", Durations.format(Duration.ofSeconds(249)));
        assertEquals("15m", Durations.format(Duration.ofMinutes(15)));
    }

    @Test
    void hoursDropTheSeconds() {
        assertEquals("1h", Durations.format(Duration.ofHours(1)));
        assertEquals("1h 5m", Durations.format(Duration.ofSeconds(3600 + 5 * 60 + 30)));
    }

    @Test
    void daysDropTheMinutes() {
        assertEquals("2d 3h", Durations.format(Duration.ofHours(51).plusMinutes(20)));
        assertEquals("7d", Durations.format(Duration.ofDays(7).plusMinutes(1)));
    }

    @Test
    void partOfASecondRoundsUp() {
        assertEquals("1s", Durations.format(Duration.ofMillis(1)));
        assertEquals("10s", Durations.format(Duration.ofMillis(9_100)));
    }

    @Test
    void parsesEachUnitAndTheirSum() {
        assertEquals(Optional.of(Duration.ofSeconds(45)), Durations.parse("45s"));
        assertEquals(Optional.of(Duration.ofMinutes(30)), Durations.parse("30m"));
        assertEquals(Optional.of(Duration.ofHours(2)), Durations.parse("2H"));
        assertEquals(Optional.of(Duration.ofDays(7)), Durations.parse("7d"));
        assertEquals(Optional.of(Duration.ofDays(14)), Durations.parse("2w"));
        assertEquals(Optional.of(Duration.ofMinutes(90)), Durations.parse("1h30m"));
    }

    @Test
    void refusesAnythingElse() {
        assertEquals(Optional.empty(), Durations.parse(""));
        assertEquals(Optional.empty(), Durations.parse("30"));
        assertEquals(Optional.empty(), Durations.parse("m"));
        assertEquals(Optional.empty(), Durations.parse("1h 30m"));
        assertEquals(Optional.empty(), Durations.parse("0m"));
        assertEquals(Optional.empty(), Durations.parse("koth"));
        assertEquals(Optional.empty(), Durations.parse("-5m"));
    }
}
