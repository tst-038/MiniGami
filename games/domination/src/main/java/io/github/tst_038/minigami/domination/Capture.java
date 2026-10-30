package io.github.tst_038.minigami.domination;

import java.util.List;
import java.util.Optional;

/**
 * Who owns one capture point, and who is taking it. A team other than the owner that stands on the
 * point alone for the capture time takes it. A kept point stays its owner's until then; one not kept is
 * owned only while its owner stands on it alone. Contesting it pauses the taking, leaving it empty
 * starts it over, and the owner alone on it fends it off.
 *
 * @param <T> what tells teams apart
 */
final class Capture<T> {

    private final int seconds;
    private final boolean keep;
    private Optional<T> owner = Optional.empty();
    private Optional<T> taker = Optional.empty();
    private int progress;

    /**
     * @param seconds how long a team must stand on the point alone to take it; 1 takes it at once
     * @param keep whether the owner keeps it once it steps off or another team steps on
     */
    Capture(int seconds, boolean keep) {
        this.seconds = seconds;
        this.keep = keep;
    }

    /**
     * One second passes with these teams on the point.
     *
     * @param present the distinct teams standing on it
     * @return whether the point changed hands
     */
    boolean tick(List<T> present) {
        if (!keep && !(present.size() == 1 && owner.equals(Optional.of(present.getFirst())))) {
            owner = Optional.empty();
        }
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
