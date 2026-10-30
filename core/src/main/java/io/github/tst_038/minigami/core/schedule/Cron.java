package io.github.tst_038.minigami.core.schedule;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Optional;

/**
 * When something happens, written as the five fields of a cron line: minute, hour, day of the month,
 * month and day of the week. Each field is {@code *}, a number, a range {@code a-b}, any of those with a
 * step {@code /n}, or a comma separated list of them. Day of the week runs from 0 (Sunday) to 7 (Sunday
 * again). As in cron, when both day fields are limited, a day matching either one counts.
 */
public final class Cron {

    private static final int[][] BOUNDS = {{0, 59}, {0, 23}, {1, 31}, {1, 12}, {0, 7}};
    private static final String[] NAMES = {"minute", "hour", "day", "month", "weekday"};

    private final BitSet[] fields;
    private final boolean anyDay;
    private final boolean anyWeekday;
    private final String text;

    private Cron(BitSet[] fields, boolean anyDay, boolean anyWeekday, String text) {
        this.fields = fields;
        this.anyDay = anyDay;
        this.anyWeekday = anyWeekday;
        this.text = text;
    }

    /** @throws IllegalArgumentException with a message fit to show whoever wrote it, if the text isn't a valid cron line */
    public static Cron parse(String text) {
        var parts = text.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("needs 5 fields (minute hour day month weekday), not " + parts.length);
        }
        var fields = new BitSet[5];
        for (int i = 0; i < 5; i++) {
            fields[i] = field(parts[i], i);
        }
        if (fields[4].get(7)) {
            fields[4].set(0);
        }
        return new Cron(fields, parts[2].equals("*"), parts[4].equals("*"), String.join(" ", parts));
    }

    private static BitSet field(String text, int index) {
        int min = BOUNDS[index][0];
        int max = BOUNDS[index][1];
        var bits = new BitSet();
        for (var item : text.split(",", -1)) {
            var stepParts = item.split("/", -1);
            if (stepParts.length > 2) {
                throw invalid(index, item);
            }
            int step = stepParts.length == 2 ? number(stepParts[1], index, item) : 1;
            if (step < 1) {
                throw invalid(index, item);
            }
            var range = stepParts[0];
            int from;
            int to;
            if (range.equals("*")) {
                from = min;
                to = max;
            } else if (range.contains("-")) {
                var ends = range.split("-", -1);
                if (ends.length != 2) {
                    throw invalid(index, item);
                }
                from = number(ends[0], index, item);
                to = number(ends[1], index, item);
            } else {
                from = number(range, index, item);
                to = stepParts.length == 2 ? max : from;
            }
            if (from < min || to > max || from > to) {
                throw new IllegalArgumentException(NAMES[index] + " '" + item + "' must stay within " + min + "-" + max);
            }
            for (int value = from; value <= to; value += step) {
                bits.set(value);
            }
        }
        return bits;
    }

    private static int number(String text, int index, String item) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw invalid(index, item);
        }
    }

    private static IllegalArgumentException invalid(int index, String item) {
        return new IllegalArgumentException("'" + item + "' is not a valid " + NAMES[index]);
    }

    /**
     * The first whole minute after the given time that matches; empty if none does within the next
     * eight years, such as for the 30th of February.
     */
    public Optional<LocalDateTime> next(LocalDateTime after) {
        var time = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        var limit = time.plusYears(8);
        while (time.isBefore(limit)) {
            if (!fields[3].get(time.getMonthValue())) {
                time = time.withDayOfMonth(1).withHour(0).withMinute(0).plusMonths(1);
            } else if (!dayMatches(time)) {
                time = time.withHour(0).withMinute(0).plusDays(1);
            } else if (!fields[1].get(time.getHour())) {
                time = time.withMinute(0).plusHours(1);
            } else if (!fields[0].get(time.getMinute())) {
                time = time.plusMinutes(1);
            } else {
                return Optional.of(time);
            }
        }
        return Optional.empty();
    }

    private boolean dayMatches(LocalDateTime time) {
        boolean day = fields[2].get(time.getDayOfMonth());
        boolean weekday = fields[4].get(time.getDayOfWeek().getValue() % 7);
        if (anyDay || anyWeekday) {
            return day && weekday;
        }
        return day || weekday;
    }

    @Override
    public String toString() {
        return text;
    }
}
