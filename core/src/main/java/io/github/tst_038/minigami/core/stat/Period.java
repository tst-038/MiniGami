package io.github.tst_038.minigami.core.stat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/** A span stats are summed over, up to today. Weeks start on Monday; days are in the server's time. */
public enum Period {
    ALL_TIME,
    WEEK,
    MONTH;

    /** The first day counted; empty for all time. */
    public Optional<LocalDate> firstDay(LocalDate today) {
        return switch (this) {
            case ALL_TIME -> Optional.empty();
            case WEEK -> Optional.of(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            case MONTH -> Optional.of(today.withDayOfMonth(1));
        };
    }
}
