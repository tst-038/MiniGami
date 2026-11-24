package io.github.tst_038.minigami.koth.schedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** When KOTH is due to run. Pure and stateless: the answer depends only on the time asked about. */
public sealed interface Timetable permits CronTimetable, CycleTimetable {

    /** The window that has started and not yet ended; the earliest one if several overlap. */
    Optional<Window> current(LocalDateTime now);

    /** The first window that starts after the given time. */
    Optional<Window> next(LocalDateTime now);

    /** How long one window lasts, which is also how long a forced start runs by default. */
    Duration length();
}
