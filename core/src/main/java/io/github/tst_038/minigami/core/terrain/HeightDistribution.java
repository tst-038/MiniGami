package io.github.tst_038.minigami.core.terrain;

import java.util.random.RandomGenerator;

/**
 * Where, between two heights, something is most likely to be found. Heights are blocks above the
 * bottom of the area, both ends included.
 */
public sealed interface HeightDistribution {

    int min();

    int max();

    int pick(RandomGenerator random);

    /** Every height equally likely (coal, copper). */
    record Uniform(int min, int max) implements HeightDistribution {

        public Uniform {
            checkRange(min, max);
        }

        @Override
        public int pick(RandomGenerator random) {
            return min + random.nextInt(max - min + 1);
        }
    }

    /** Most likely in the middle, thinning to the ends (diamonds, gold, lapis). */
    record Triangle(int min, int max) implements HeightDistribution {

        public Triangle {
            checkRange(min, max);
        }

        @Override
        public int pick(RandomGenerator random) {
            int span = max - min + 1;
            return min + (random.nextInt(span) + random.nextInt(span)) / 2;
        }
    }

    /** Equally likely across the middle {@code plateau} blocks and thinning towards the ends (iron). */
    record Trapezoid(int min, int max, int plateau) implements HeightDistribution {

        public Trapezoid {
            checkRange(min, max);
            if (plateau < 0) {
                throw new IllegalArgumentException("the plateau cannot be negative");
            }
        }

        @Override
        public int pick(RandomGenerator random) {
            int span = max - min;
            if (plateau >= span) {
                return min + random.nextInt(span + 1);
            }
            int slope = (span - plateau) / 2;
            int rest = span - slope;
            return min + random.nextInt(rest + 1) + random.nextInt(slope + 1);
        }
    }

    private static void checkRange(int min, int max) {
        if (min < 0 || max < min) {
            throw new IllegalArgumentException("a height range needs 0 <= min <= max, got " + min + " to " + max);
        }
    }
}
