package io.github.tst_038.minigami.koth.text;

import java.time.Duration;

/** Spells a length of time the way players read it: "1h 5m", "4m 9s", "9s". */
public final class Durations {

    private Durations() {
    }

    /** Rounds up to whole seconds; zero and negative lengths read "0s". At an hour or more the seconds are left out. */
    public static String format(Duration length) {
        if (!length.isPositive()) {
            return "0s";
        }
        long seconds = length.plusNanos(999_999_999).getSeconds();
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        if (hours > 0) {
            return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
        }
        return rest + "s";
    }
}
