package io.github.tst_038.minigami.core.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/** Decides where in a container loot goes, so a chest doesn't fill from its first slot on. */
public final class Scatter {

    private Scatter() {
    }

    /**
     * Picks distinct slots, in random order, out of the free ones.
     *
     * @return {@code count} slots, or all the free ones if there are fewer
     */
    public static List<Integer> slots(List<Integer> free, int count, RandomGenerator random) {
        var shuffled = new ArrayList<>(free);
        Collections.shuffle(shuffled, random);
        return List.copyOf(shuffled.subList(0, Math.min(count, shuffled.size())));
    }
}
