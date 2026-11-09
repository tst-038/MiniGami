package io.github.tst_038.minigami.core.floor;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

/**
 * The floors of a place that stack on top of each other, as the heights players can stand at. Levels are
 * numbered from the highest floor there was, starting at 1, so a level keeps its number when a floor above it breaks away.
 */
public final class Floors {

    private final List<Integer> heights;
    private final int standing;

    /** @param heights the height of each floor, in any order */
    public Floors(Collection<Integer> heights) {
        this(heights.stream().distinct().sorted().toList(), (int) heights.stream().distinct().count());
    }

    private Floors(List<Integer> heights, int standing) {
        this.heights = heights;
        this.standing = standing;
    }

    /** How many floors are still standing. */
    public int count() {
        return standing;
    }

    /**
     * The floors as counted later: the levels stay numbered as they were, with any new height added, and the
     * count is of what stands now.
     */
    public Floors recount(Floors later) {
        return new Floors(java.util.stream.Stream.concat(heights.stream(), later.heights.stream()).distinct().sorted().toList(), later.standing);
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
