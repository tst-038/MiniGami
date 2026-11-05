package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlacersTest {

    private static final int SIZE = 80;
    private static final int HEIGHT = 80;
    private static final ShapeSpec SHAPE = new ShapeSpec(
            new ShapeSpec.HeightSpec(40, new ShapeSpec.Wave(5, 40, 3), new ShapeSpec.Wave(1, 10, 2)),
            30, "minecraft:stone", "minecraft:deepslate", "minecraft:bedrock", 12, 5);
    private static final List<Anchor> ANCHORS = List.of(new Anchor(20, 42, 20));
    private static final TreeSpec OAK = new TreeSpec("trees", "minecraft:oak_log", "minecraft:oak_leaves",
            new IntRange(4, 6), IntRange.of(5), Set.of("minecraft:grass_block"));
    private static final PoolSpec WATER = new PoolSpec("water-pools", "minecraft:water", IntRange.of(2), new IntRange(3, 5), 3);
    private static final DecorSpec FLOWERS = new DecorSpec("flowers",
            Map.of("minecraft:dandelion", 1, "minecraft:poppy", 1), 0.2, Set.of("minecraft:grass_block"));

    private final BlockPalette palette = new BlockPalette();
    private final Volume volume = new Volume(SIZE, HEIGHT, SIZE);
    private final Ground ground = Shape.apply(volume, palette, SHAPE, ANCHORS, 3);

    {
        Surface.apply(volume, palette, SHAPE, TestStyles.SURFACE, ground, 3);
    }

    private int count(String block) {
        return volume.count(palette.index(block));
    }

    @Test
    void poolsAreFlushWithTheGroundAndNeverOnPads() {
        int placed = PoolPlacer.apply(volume, palette, SHAPE, WATER, 3, ground, ANCHORS, 7);
        assertTrue(placed >= 1 && placed <= 3, "pools " + placed);
        int water = count("minecraft:water");
        assertTrue(water > 0);

        short fluid = palette.index("minecraft:water");
        short air = palette.index(BlockPalette.AIR);
        int dryWater = 0;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                for (int y = 31; y < HEIGHT; y++) {
                    if (volume.get(x, y, z) == fluid) {
                        dryWater++;
                        assertTrue(Math.hypot(x - 20, z - 20) > SHAPE.padRadius(), "water on the pad at " + x + "," + z);
                        assertTrue(volume.get(x, y + 1, z) == air || volume.get(x, y + 1, z) == fluid, "open to the sky at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertTrue(dryWater > 0, "pools above sea level");
    }

    @Test
    void treesStandOnGrassApartFromEachOther() {
        int planted = TreePlacer.apply(volume, palette, SHAPE, OAK, 5, ground, ANCHORS, 7);
        assertTrue(planted >= 3 && planted <= 5, "trees " + planted);
        assertTrue(count("minecraft:oak_log") >= planted * 4);
        assertTrue(count("minecraft:oak_leaves") > planted * 10);

        short log = palette.index("minecraft:oak_log");
        short grass = palette.index("minecraft:grass_block");
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                for (int y = 1; y < HEIGHT; y++) {
                    if (volume.get(x, y, z) == log && volume.get(x, y - 1, z) != log) {
                        assertEquals(grass, volume.get(x, y - 1, z), "trunk base at " + x + "," + y + "," + z);
                        assertTrue(Math.hypot(x - 20, z - 20) > SHAPE.padRadius());
                    }
                }
            }
        }
    }

    @Test
    void bareColumnsNeedOpenSides() {
        var cactus = new TreeSpec("cactus", "minecraft:cactus", null, new IntRange(2, 3), IntRange.of(8), Set.of("minecraft:grass_block"));
        int planted = TreePlacer.apply(volume, palette, SHAPE, cactus, 8, ground, ANCHORS, 7);
        assertTrue(planted > 0);
        assertEquals(0, count("minecraft:oak_leaves"));
        assertTrue(count("minecraft:cactus") >= planted * 2);
    }

    @Test
    void decorSprinklesPlantsOnGrassOnly() {
        int placed = Decor.apply(volume, palette, FLOWERS, ground, 7);
        assertEquals(placed, count("minecraft:dandelion") + count("minecraft:poppy"));
        assertTrue(placed > 100);
        assertTrue(count("minecraft:dandelion") > 0 && count("minecraft:poppy") > 0);
    }

    @Test
    void placersAreDeterministic() {
        var palette2 = new BlockPalette();
        var volume2 = new Volume(SIZE, HEIGHT, SIZE);
        var ground2 = Shape.apply(volume2, palette2, SHAPE, ANCHORS, 3);
        Surface.apply(volume2, palette2, SHAPE, TestStyles.SURFACE, ground2, 3);
        PoolPlacer.apply(volume, palette, SHAPE, WATER, 3, ground, ANCHORS, 7);
        TreePlacer.apply(volume, palette, SHAPE, OAK, 5, ground, ANCHORS, 7);
        Decor.apply(volume, palette, FLOWERS, ground, 7);
        PoolPlacer.apply(volume2, palette2, SHAPE, WATER, 3, ground2, ANCHORS, 7);
        TreePlacer.apply(volume2, palette2, SHAPE, OAK, 5, ground2, ANCHORS, 7);
        Decor.apply(volume2, palette2, FLOWERS, ground2, 7);
        assertArrayEquals(volume.cells(), volume2.cells());
    }

    @Test
    void refusesNonsenseSpecs() {
        assertThrows(IllegalArgumentException.class, () -> new PoolSpec("p", "minecraft:water", IntRange.of(1), IntRange.of(0), 2));
        assertThrows(IllegalArgumentException.class, () -> new TreeSpec("t", "a", "b", IntRange.of(0), IntRange.of(1), Set.of("x")));
        assertThrows(IllegalArgumentException.class, () -> new TreeSpec("t", "a", "b", IntRange.of(2), IntRange.of(1), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new DecorSpec("d", Map.of("a", 1), 1.5, Set.of("x")));
        assertThrows(IllegalArgumentException.class, () -> new DecorSpec("d", Map.of(), 0.5, Set.of("x")));
    }
}
