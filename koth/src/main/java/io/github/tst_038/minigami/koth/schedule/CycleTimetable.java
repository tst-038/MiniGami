package io.github.tst_038.minigami.koth.schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * KOTH runs for {@code active}, pauses for {@code pause}, and repeats around the clock. Cycles are counted
 * from a fixed midnight, so they are the same after a restart and line up with the clock when the period
 * divides a day. Lengths are whole seconds.
 */
public record CycleTimetable(Duration active, Duration pause) implements Timetable {

    private static final LocalDateTime ANCHOR = LocalDate.EPOCH.atStartOfDay();

    public CycleTimetable {
        if (active.compareTo(Duration.ofMinutes(1)) < 0 || pause.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("a cycle needs at least a minute active and a minute of break, not " + active + " and " + pause);
        }
    }

    @Override
    public Optional<Window> current(LocalDateTime now) {
        var window = cycle(now);
        return window.contains(now) ? Optional.of(window) : Optional.empty();
    }

    @Override
    public Optional<Window> next(LocalDateTime now) {
        var start = cycle(now).start().plus(period());
        return Optional.of(new Window(start, start.plus(active)));
    }

    @Override
    public Duration length() {
        return active;
    }

    /** The window of the cycle that {@code now} falls in, whether or not it is inside the active part. */
    private Window cycle(LocalDateTime now) {
        long period = period().getSeconds();
        long cycles = Math.floorDiv(Duration.between(ANCHOR, now).getSeconds(), period);
        var start = ANCHOR.plusSeconds(cycles * period);
        return new Window(start, start.plus(active));
    }

    private Duration period() {
        return active.plus(pause);
    }
}
