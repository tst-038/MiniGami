package io.github.tst_038.minigami.domination;

import java.util.List;
import java.util.Optional;

/**
 * Who owns one capture point, and who is taking it. A team other than the owner that stands on the
 * point alone for the capture time takes it; the owner keeps it until then. Contesting it pauses the
 * taking, leaving it empty starts it over, and the owner alone on it fends it off.
 *
 * @param <T> what tells teams apart
 */
final class Capture<T> {

    private final int seconds;
    private Optional<T> owner = Optional.empty();
    private Optional<T> taker = Optional.empty();
    private int progress;

    /** @param seconds how long a team must stand on the point alone to take it; 1 takes it at once */
    Capture(int seconds) {
        this.seconds = seconds;
    }

    /**
     * One second passes with these teams on the point.
     *
     * @param present the distinct teams standing on it
     * @return whether the point changed hands
     */
    boolean tick(List<T> present) {
        if (present.size() > 1) {
            return false;
        }
        if (present.isEmpty() || owner.equals(Optional.of(present.getFirst()))) {
            taker = Optional.empty();
            progress = 0;
            return false;
        }
        var team = present.getFirst();
        if (!taker.equals(Optional.of(team))) {
            taker = Optional.of(team);
            progress = 0;
        }
        if (++progress < seconds) {
            return false;
        }
        owner = taker;
        taker = Optional.empty();
        progress = 0;
        return true;
    }

    Optional<T> owner() {
        return owner;
    }

    /** The team taking the point, while it is not done. */
    Optional<T> taker() {
        return taker;
    }

    /** Seconds the taker has stood on it alone so far. */
    int progress() {
        return progress;
    }

    int seconds() {
        return seconds;
    }
}
