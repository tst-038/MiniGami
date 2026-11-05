package io.github.tst_038.minigami.core.terrain;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.Map;

/**
 * What the top of the land is made of.
 *
 * @param top      the block on the surface (grass)
 * @param under    the blocks under it, for {@code depth} layers including the top (dirt)
 * @param depth    how many layers the top covers, chosen per column
 * @param shore    the block on and near the waterline (sand)
 * @param shoreUnder what holds the shore up, two layers deep (sandstone under sand, so it never falls)
 * @param lakebed  blocks on the floor of deeper water, with how common each is (gravel 3, sand 2, clay 1)
 */
public record SurfaceSpec(String top, String under, IntRange depth, String shore, String shoreUnder, Map<String, Integer> lakebed) {

    public SurfaceSpec {
        if (depth.min() < 1) {
            throw new IllegalArgumentException("the surface needs at least one layer");
        }
        if (lakebed.isEmpty() || lakebed.values().stream().anyMatch(weight -> weight < 1)) {
            throw new IllegalArgumentException("the lakebed needs at least one block, each with a weight of at least 1");
        }
        lakebed = Map.copyOf(lakebed);
    }
}
