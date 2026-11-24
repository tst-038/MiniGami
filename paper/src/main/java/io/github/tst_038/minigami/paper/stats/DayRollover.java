package io.github.tst_038.minigami.paper.stats;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Runs something at each midnight of the clock's time zone, such as loading the stats of the week and month again once the date has
 * changed. Waits until the next midnight and then schedules the one after, so it stays exact across days of 23 or 25 hours.
 */
public final class DayRollover {

    /** Runs a task once after a delay, on whichever thread suits it. */
    public interface Timer {
        void after(Duration delay, Runnable task);
    }

    private final Clock clock;
    private final Timer timer;
    private final Runnable onNewDay;
    private LocalDate day;

    public DayRollover(Clock clock, Timer timer, Runnable onNewDay) {
        this.clock = clock;
        this.timer = timer;
        this.onNewDay = onNewDay;
    }

    /** Starts waiting for the next midnight. */
    public void start() {
        day = LocalDate.now(clock);
        timer.after(untilNextDay(clock), this::fire);
    }

    /** How long until the next day starts: the first moment of it, which is after midnight if a clock change skips midnight. */
    static Duration untilNextDay(Clock clock) {
        var now = ZonedDateTime.now(clock);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(clock.getZone()));
    }

    private void fire() {
        // A timer that goes off a moment early finds the date unchanged; it just waits for the rest.
        var today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            onNewDay.run();
        }
        timer.after(untilNextDay(clock), this::fire);
    }
}
