package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.core.schedule.Cron;
import io.github.tst_038.minigami.koth.schedule.CronTimetable;
import io.github.tst_038.minigami.koth.schedule.CycleTimetable;
import io.github.tst_038.minigami.koth.schedule.Timetable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Reads the {@code schedule} section: when KOTH runs, and when to warn before it does. */
public final class TimetableReader {

    private static final int MAX_MINUTES = 10_080;
    private static final List<Duration> DEFAULT_WARNINGS = List.of(Duration.ofMinutes(60), Duration.ofMinutes(15), Duration.ofMinutes(2));

    private enum Type { CRON, CYCLE }

    private TimetableReader() {
    }

    /** The timetable; empty, with the reasons reported to the reader, when it can't be built. */
    public static Optional<Timetable> read(ConfigReader schedule) {
        return switch (schedule.choice("type", Type.class)) {
            case CRON -> cron(schedule);
            case CYCLE -> Optional.of(new CycleTimetable(minutes(schedule, "active-minutes"), minutes(schedule, "break-minutes")));
        };
    }

    /** How long before a start to warn; 60, 15 and 2 minutes when the key is left out. */
    public static List<Duration> warnings(ConfigReader schedule) {
        if (!schedule.has("warn-minutes")) {
            return DEFAULT_WARNINGS;
        }
        var warnings = new ArrayList<Duration>();
        for (String text : schedule.strings("warn-minutes")) {
            var minutes = wholeMinutes(text);
            if (minutes.isPresent()) {
                warnings.add(Duration.ofMinutes(minutes.getAsInt()));
            } else {
                schedule.problem("warn-minutes", "'" + text + "' must be a whole number of minutes from 1 to " + MAX_MINUTES);
            }
        }
        return warnings;
    }

    private static OptionalInt wholeMinutes(String text) {
        try {
            int minutes = Integer.parseInt(text.strip());
            return minutes >= 1 && minutes <= MAX_MINUTES ? OptionalInt.of(minutes) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static Optional<Timetable> cron(ConfigReader schedule) {
        var length = minutes(schedule, "length-minutes");
        var lines = schedule.has("when") ? schedule.stringsOrOne("when") : List.<String>of();
        var starts = new ArrayList<Cron>();
        for (String line : lines) {
            try {
                starts.add(Cron.parse(line));
            } catch (IllegalArgumentException e) {
                schedule.problem("when", "'" + line + "': " + e.getMessage());
            }
        }
        if (lines.isEmpty()) {
            schedule.problem("when", schedule.has("when") ? "needs at least one cron line" : "is missing");
        }
        return starts.isEmpty() ? Optional.empty() : Optional.of(new CronTimetable(starts, length));
    }

    private static Duration minutes(ConfigReader schedule, String key) {
        return Duration.ofMinutes(schedule.integer(key, 1, MAX_MINUTES));
    }
}
