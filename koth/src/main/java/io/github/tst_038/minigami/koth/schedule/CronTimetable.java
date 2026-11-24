package io.github.tst_038.minigami.koth.schedule;

import io.github.tst_038.minigami.core.schedule.Cron;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * KOTH starts at every time any of the cron lines names and runs for {@code length}. Windows that overlap
 * merge in effect: when one ends inside another, the other starts at once, for what is left of it.
 */
public record CronTimetable(List<Cron> starts, Duration length) implements Timetable {

    public CronTimetable {
        starts = List.copyOf(starts);
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("a cron timetable needs at least one start");
        }
        if (length.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("a window must last at least a minute, not " + length);
        }
    }

    @Override
    public Optional<Window> current(LocalDateTime now) {
        // The first start after now - length is the earliest one whose window hasn't ended yet.
        return starts.stream()
                .flatMap(cron -> cron.next(now.minus(length)).stream())
                .filter(start -> !start.isAfter(now))
                .min(Comparator.naturalOrder())
                .map(this::window);
    }

    @Override
    public Optional<Window> next(LocalDateTime now) {
        return starts.stream()
                .flatMap(cron -> cron.next(now).stream())
                .min(Comparator.naturalOrder())
                .map(this::window);
    }

    private Window window(LocalDateTime start) {
        return new Window(start, start.plus(length));
    }
}
