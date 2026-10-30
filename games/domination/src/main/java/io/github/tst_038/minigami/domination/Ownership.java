package io.github.tst_038.minigami.domination;

import java.util.List;
import java.util.Optional;

/** Who owns a capture point after one more second. */
final class Ownership {

    private Ownership() {
    }

    /**
     * A team alone on the point takes it. Otherwise a kept point stays with its owner, and a point that
     * is not kept has none.
     *
     * @param present the distinct teams standing on the point
     * @param keep whether a point stays captured when its owner steps off
     */
    static <T> Optional<T> next(Optional<T> owner, List<T> present, boolean keep) {
        if (present.size() == 1) {
            return Optional.of(present.getFirst());
        }
        return keep ? owner : Optional.empty();
    }
}
