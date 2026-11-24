package io.github.tst_038.minigami.koth.schedule;

import java.time.Duration;
import java.time.LocalDateTime;

/** One stretch of time KOTH is meant to run, server time. The start counts, the end doesn't. */
public record Window(LocalDateTime start, LocalDateTime end) {

    public Window {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("a window must end after it starts: " + start + " to " + end);
        }
    }

    public boolean contains(LocalDateTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }

    /** How long until the end; zero once it has passed. */
    public Duration left(LocalDateTime now) {
        return now.isBefore(end) ? Duration.between(now, end) : Duration.ZERO;
    }
}
