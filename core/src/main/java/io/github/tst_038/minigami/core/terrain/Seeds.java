package io.github.tst_038.minigami.core.terrain;

import java.util.SplittableRandom;

/**
 * Turns one seed into many independent ones, so each part of the generation (an area, an attempt, a
 * stage, an ore) draws from its own random stream. Changing how many trees are placed then never
 * moves a single diamond.
 *
 * <p>Only algorithms with a fixed specification are used (SplitMix64 mixing, {@link SplittableRandom},
 * {@link String#hashCode()}), so the same seed gives the same land on every JVM.
 */
public final class Seeds {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Seeds() {
    }

    /** The seed for the part of the generation named by {@code path}: numbers and names, outermost first. */
    public static long derive(long seed, Object... path) {
        long result = mix(seed);
        for (var part : path) {
            result = mix(result + GOLDEN_GAMMA + mix(partValue(part)));
        }
        return result;
    }

    public static SplittableRandom random(long seed, Object... path) {
        return new SplittableRandom(derive(seed, path));
    }

    private static long partValue(Object part) {
        return switch (part) {
            case Number number -> number.longValue();
            case String text -> text.hashCode();
            default -> throw new IllegalArgumentException("A seed path holds numbers and names, got " + part);
        };
    }

    /** The SplitMix64 finalizer: every input bit affects every output bit. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
