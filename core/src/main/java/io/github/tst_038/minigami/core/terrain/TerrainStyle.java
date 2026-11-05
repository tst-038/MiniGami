package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything that decides what generated land looks like: its shape, surface, caves and what is
 * scattered over and under it. A style is data; the same style with the same seed gives the same land.
 *
 * @param caves  tunnels, or empty for solid ground
 * @param groups named sets of block ids that quotas may count together ("ores")
 */
public record TerrainStyle(String id, ShapeSpec shape, SurfaceSpec surface, Optional<CaveSpec> caves, List<OreSpec> ores,
                           List<PoolSpec> pools, List<TreeSpec> trees, List<DecorSpec> decor, Map<String, List<String>> groups) {

    public TerrainStyle {
        ores = List.copyOf(ores);
        pools = List.copyOf(pools);
        trees = List.copyOf(trees);
        decor = List.copyOf(decor);
        groups = Map.copyOf(groups);
        var names = new ArrayList<String>();
        ores.forEach(ore -> names.add(ore.name()));
        pools.forEach(pool -> names.add(pool.name()));
        trees.forEach(tree -> names.add(tree.name()));
        decor.forEach(item -> names.add(item.name()));
        var seen = new HashSet<String>();
        for (var name : names) {
            if (!seen.add(name)) {
                throw new IllegalArgumentException("style '" + id + "': '" + name + "' names more than one ore or placer");
            }
        }
    }
}
