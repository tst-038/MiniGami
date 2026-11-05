package io.github.tst_038.minigami.core.range;

import java.util.random.RandomGenerator;

/** A whole number between two bounds, both included: how many times a pool draws, or how many of an item. */
public record IntRange(int min, int max) {

    public IntRange {
        if (min < 0 || min > max) {
            throw new IllegalArgumentException("A range needs 0 <= min <= max, got " + min + " to " + max);
        }
    }

    public static IntRange of(int exactly) {
        return new IntRange(exactly, exactly);
    }

    /** A number in the range, each equally likely. */
    public int pick(RandomGenerator random) {
        return min == max ? min : random.nextInt(min, max + 1);
    }
}
