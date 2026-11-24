package io.github.tst_038.minigami.koth.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
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
    void partOfASecondRoundsUp() {
        assertEquals("1s", Durations.format(Duration.ofMillis(1)));
        assertEquals("10s", Durations.format(Duration.ofMillis(9_100)));
    }
}
