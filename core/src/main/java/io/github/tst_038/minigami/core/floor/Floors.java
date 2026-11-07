package io.github.tst_038.minigami.core.floor;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

/**
 * The floors of a place that stack on top of each other, as the heights players can stand at. Levels are
 * numbered from the highest floor that is left, starting at 1, so the top is always level 1 and the rest renumber as floors break away.
 */
public final class Floors {

    private final List<Integer> heights;

    /** @param heights the height of each floor, in any order */
    public Floors(Collection<Integer> heights) {
        this.heights = heights.stream().distinct().sorted().toList();
    }

    /** How many floors there are. */
    public int count() {
        return heights.size();
    }

    /**
     * @param y the height of something's feet
     * @return the level of the highest floor at or below it, or empty if it is below them all
     */
    public OptionalInt levelAt(double y) {
        int below = 0;
        while (below < heights.size() && heights.get(below) <= y) {
            below++;
        }
        // The floors are sorted lowest first, so the one found is counted from the top.
        return below == 0 ? OptionalInt.empty() : OptionalInt.of(heights.size() - below + 1);
    }
}
