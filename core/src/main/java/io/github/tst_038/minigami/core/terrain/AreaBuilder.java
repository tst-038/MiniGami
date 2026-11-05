package io.github.tst_038.minigami.core.terrain;

import java.util.HashMap;
import java.util.Map;

/** Runs the stages in their fixed order for one area and one attempt. Order is physics: land, then what is cut into it, then what stands on it. */
final class AreaBuilder {

    /** The finished volume and how many of each placer's items fit. */
    record Built(Volume volume, BlockPalette palette, Map<String, Integer> placed) {
    }

    private AreaBuilder() {
    }

    static Built build(TerrainStyle style, AreaRequest area, Map<QuotaKeys.Knob, Integer> knobs, long seed) {
        var palette = new BlockPalette();
        var volume = new Volume(area.sizeX(), area.sizeY(), area.sizeZ());
        var placed = new HashMap<String, Integer>();

        var ground = Shape.apply(volume, palette, style.shape(), area.anchors(), seed);
        Surface.apply(volume, palette, style.shape(), style.surface(), ground, seed);
        style.caves().ifPresent(caves -> Caves.apply(volume, palette, style.shape(), caves, ground, seed));
        for (var pool : style.pools()) {
            int count = knobs.get(new QuotaKeys.Knob(QuotaKeys.Kind.POOL, pool.name()));
            placed.put(pool.name(), PoolPlacer.apply(volume, palette, style.shape(), pool, count, ground, area.anchors(), seed));
        }
        var ores = style.ores().stream()
                .map(ore -> ore.withVeins(knobs.get(new QuotaKeys.Knob(QuotaKeys.Kind.ORE, ore.name()))))
                .toList();
        VeinPlacer.apply(volume, palette, style.shape(), ores, seed);
        for (var tree : style.trees()) {
            int count = knobs.get(new QuotaKeys.Knob(QuotaKeys.Kind.TREE, tree.name()));
            placed.put(tree.name(), TreePlacer.apply(volume, palette, style.shape(), tree, count, ground, area.anchors(), seed));
        }
        for (var decor : style.decor()) {
            placed.put(decor.name(), Decor.apply(volume, palette, decor, ground, seed));
        }
        return new Built(volume, palette, placed);
    }
}
