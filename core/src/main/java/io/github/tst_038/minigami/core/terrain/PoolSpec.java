package io.github.tst_038.minigami.core.terrain;

import io.github.tst_038.minigami.core.range.IntRange;

/**
 * Small ponds of water or lava sunk into the land.
 *
 * @param name   what quotas call this placer ("water-pools")
 * @param fluid  the block that fills the pool
 * @param count  pools per area, unless a quota asks for a number
 * @param radius how wide each pool is, in blocks from the middle
 * @param depth  how deep the middle goes
 */
public record PoolSpec(String name, String fluid, IntRange count, IntRange radius, int depth) {

    public PoolSpec {
        if (name.isBlank()) {
            throw new IllegalArgumentException("a pool placer needs a name");
        }
        if (radius.min() < 1 || depth < 1) {
            throw new IllegalArgumentException("pool '" + name + "': radius and depth must be at least 1");
        }
    }
}
