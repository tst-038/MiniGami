package io.github.tst_038.minigami.core.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CronTest {

    // A Wednesday.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 14, 7, 30);

    private static LocalDateTime next(String cron) {
        return Cron.parse(cron).next(NOW).orElseThrow();
    }

    @Test
    void everyMinuteIsTheNextWholeMinute() {
        assertEquals(LocalDateTime.of(2026, 10, 28, 14, 8), next("* * * * *"));
    }

    @Test
    void aMinuteThatJustMatchedIsNotRepeated() {
        assertEquals(LocalDateTime.of(2026, 10, 28, 14, 9), Cron.parse("* * * * *").next(LocalDateTime.of(2026, 10, 28, 14, 8)).orElseThrow());
    }

    @Test
    void stepsCountFromTheStartOfTheRange() {
        assertEquals(LocalDateTime.of(2026, 10, 28, 14, 30), next("*/30 * * * *"));
        assertEquals(LocalDateTime.of(2026, 10, 28, 14, 20), next("5/15 * * * *"));
        assertEquals(LocalDateTime.of(2026, 10, 28, 16, 0), next("0 10-20/3 * * *"));
    }

    @Test
    void listsAndRanges() {
        assertEquals(LocalDateTime.of(2026, 10, 28, 21, 0), next("0 9,21 * * *"));
        assertEquals(LocalDateTime.of(2026, 10, 29, 9, 0), next("0 9-12 * * *"));
    }

    @Test
    void rollsOverIntoTheNextMonthAndYear() {
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), next("0 0 1 1 *"));
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), next("0 0 1 * *"));
    }

    @Test
    void weekdaysWithSundayAsZeroOrSeven() {
        assertEquals(LocalDateTime.of(2026, 11, 1, 20, 0), next("0 20 * * 0"));
        assertEquals(LocalDateTime.of(2026, 11, 1, 20, 0), next("0 20 * * 7"));
        assertEquals(LocalDateTime.of(2026, 10, 30, 20, 0), next("0 20 * * 5-6"));
    }

    @Test
    void whenBothDayFieldsAreLimitedEitherCounts() {
        // The 1st of November is a Sunday, but Friday the 30th comes first.
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 0), next("0 0 1 * 5"));
    }

    @Test
    void aDayThatNeverComesNeverMatches() {
        assertEquals(Optional.empty(), Cron.parse("0 0 30 2 *").next(NOW));
    }

    @Test
    void leapDaysAreFound() {
        assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0), next("0 0 29 2 *"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"* * * *", "60 * * * *", "* 24 * * *", "* * 0 * *", "* * * 13 *", "* * * * 8",
            "*/0 * * * *", "5-1 * * * *", "a * * * *", "1-2-3 * * * *", "1,,2 * * * *", "*/2/3 * * * *"})
    void rejectsWhatIsNotACronLine(String text) {
        assertThrows(IllegalArgumentException.class, () -> Cron.parse(text));
    }
}
