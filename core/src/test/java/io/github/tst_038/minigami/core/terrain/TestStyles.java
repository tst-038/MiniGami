package io.github.tst_038.minigami.core.terrain;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.Map;

/** Small specs shared by the terrain tests. */
final class TestStyles {

    static final ShapeSpec SHAPE = new ShapeSpec(
            new ShapeSpec.HeightSpec(32, new ShapeSpec.Wave(16, 48, 4), new ShapeSpec.Wave(2, 12, 2)),
            30, "minecraft:stone", "minecraft:deepslate", "minecraft:bedrock", 12, 5);

    static final SurfaceSpec SURFACE = new SurfaceSpec(
            "minecraft:grass_block", "minecraft:dirt", new IntRange(3, 5),
            "minecraft:sand", "minecraft:sandstone",
            Map.of("minecraft:gravel", 3, "minecraft:sand", 2, "minecraft:clay", 1));

    private TestStyles() {
    }
}
