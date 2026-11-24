package io.github.tst_038.minigami.koth.hill;

/**
 * What holding the hill earns: {@code points} every {@code everySeconds} seconds.
 *
 * @param points points earned each time; 1 or more
 * @param everySeconds seconds between earnings; 1 to 3600
 */
public record Scoring(long points, int everySeconds) {

    public Scoring {
        if (points < 1) {
            throw new IllegalArgumentException("points must be at least 1: " + points);
        }
        if (everySeconds < 1 || everySeconds > 3600) {
            throw new IllegalArgumentException("every-seconds must be 1 to 3600: " + everySeconds);
        }
    }
}
