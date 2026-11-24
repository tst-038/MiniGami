package io.github.tst_038.minigami.koth.text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Writes a point in server time for players, such as {@code Mon 20:00}, the way MiniGami's schedules do. */
public final class Moments {

    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);

    private Moments() {
    }

    public static String format(LocalDateTime moment) {
        return DAY_AND_TIME.format(moment);
    }
}
