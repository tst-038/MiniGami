package io.github.tst_038.minigami.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PeriodTest {

    @Test
    void weekStartsOnMonday() {
        var monday = LocalDate.of(2026, 11, 23);
        var sunday = LocalDate.of(2026, 11, 29);

        assertEquals(Optional.of(monday), Period.WEEK.firstDay(monday));
        assertEquals(Optional.of(monday), Period.WEEK.firstDay(sunday));
    }

    @Test
    void monthStartsOnTheFirst() {
        var first = LocalDate.of(2026, 12, 1);

        assertEquals(Optional.of(first), Period.MONTH.firstDay(first));
        assertEquals(Optional.of(first), Period.MONTH.firstDay(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void allTimeHasNoFirstDay() {
        assertEquals(Optional.empty(), Period.ALL_TIME.firstDay(LocalDate.of(2026, 11, 24)));
    }
}
