package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class VeinPlacerTest {

    private static final int SIZE = 64;
    private static final int HEIGHT = 80;

    private final BlockPalette palette = new BlockPalette();
    private final Volume volume = new Volume(SIZE, HEIGHT, SIZE);

    {
        Shape.apply(volume, palette, TestStyles.SHAPE, List.of(), 9);
    }

    private static OreSpec diamond(int veins, double airDiscard) {
        return new OreSpec("diamond", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", 8, veins,
                new HeightDistribution.Triangle(5, 30), airDiscard);
    }

    private int count(String block) {
        return volume.count(palette.index(block));
    }

    @Test
    void placesClustersOfOreInRockOnly() {
        var before = volume.cells();
        VeinPlacer.apply(volume, palette, TestStyles.SHAPE, List.of(diamond(20, 0)), 1);

        int ore = count("minecraft:diamond_ore") + count("minecraft:deepslate_diamond_ore");
        assertTrue(ore > 20 && ore <= 20 * 8, "ore blocks " + ore);
        assertTrue(count("minecraft:deepslate_diamond_ore") > 0, "deep veins use the deepslate ore");

        short stone = palette.index("minecraft:stone");
        short deepslate = palette.index("minecraft:deepslate");
        short stoneOre = palette.index("minecraft:diamond_ore");
        short deepOre = palette.index("minecraft:deepslate_diamond_ore");
        var after = volume.cells();
        for (int i = 0; i < after.length; i++) {
            if (after[i] != before[i]) {
                assertTrue((before[i] == stone && after[i] == stoneOre) || (before[i] == deepslate && after[i] == deepOre));
            }
        }
    }

    @Test
    void sameSeedSameOreAndOtherOresAreUnaffected() {
        var palette2 = new BlockPalette();
        var volume2 = new Volume(SIZE, HEIGHT, SIZE);
        Shape.apply(volume2, palette2, TestStyles.SHAPE, List.of(), 9);
        VeinPlacer.apply(volume, palette, TestStyles.SHAPE, List.of(diamond(10, 0)), 5);
        VeinPlacer.apply(volume2, palette2, TestStyles.SHAPE, List.of(diamond(10, 0)), 5);
        assertArrayEquals(volume.cells(), volume2.cells());

        var coal = new OreSpec("coal", "minecraft:coal_ore", "minecraft:deepslate_coal_ore", 12, 30,
                new HeightDistribution.Uniform(5, 60), 0);
        var palette3 = new BlockPalette();
        var volume3 = new Volume(SIZE, HEIGHT, SIZE);
        Shape.apply(volume3, palette3, TestStyles.SHAPE, List.of(), 9);
        VeinPlacer.apply(volume3, palette3, TestStyles.SHAPE, List.of(diamond(10, 0), coal), 5);
        assertEquals(count("minecraft:diamond_ore") + count("minecraft:deepslate_diamond_ore"),
                volume3.count(palette3.index("minecraft:diamond_ore")) + volume3.count(palette3.index("minecraft:deepslate_diamond_ore")));
    }

    @Test
    void oreIsNotPlacedNextToAirWhenDiscardIsCertain() {
        // Carve a big cavity so plenty of veins would touch it.
        short air = palette.index(BlockPalette.AIR);
        for (int x = 10; x < 54; x++) {
            for (int z = 10; z < 54; z++) {
                for (int y = 6; y < 20; y++) {
                    volume.set(x, y, z, air);
                }
            }
        }
        VeinPlacer.apply(volume, palette, TestStyles.SHAPE, List.of(diamond(200, 1.0)), 2);
        short stoneOre = palette.index("minecraft:diamond_ore");
        short deepOre = palette.index("minecraft:deepslate_diamond_ore");
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    short block = volume.get(x, y, z);
                    if (block == stoneOre || block == deepOre) {
                        assertTrue(volume.getOr(x, y + 1, z, (short) -1) != air && volume.getOr(x, y - 1, z, (short) -1) != air
                                && volume.getOr(x + 1, y, z, (short) -1) != air && volume.getOr(x - 1, y, z, (short) -1) != air
                                && volume.getOr(x, y, z + 1, (short) -1) != air && volume.getOr(x, y, z - 1, (short) -1) != air);
                    }
                }
            }
        }
    }

    @Test
    void heightDistributionsStayInRangeAndHaveTheirShape() {
        var random = new SplittableRandom(1);
        var uniform = new HeightDistribution.Uniform(10, 30);
        var triangle = new HeightDistribution.Triangle(10, 30);
        var trapezoid = new HeightDistribution.Trapezoid(10, 30, 10);
        int uniformMiddle = 0;
        int triangleMiddle = 0;
        int trapezoidEdge = 0;
        for (int i = 0; i < 20000; i++) {
            int u = uniform.pick(random);
            int t = triangle.pick(random);
            int z = trapezoid.pick(random);
            assertTrue(u >= 10 && u <= 30 && t >= 10 && t <= 30 && z >= 10 && z <= 30);
            uniformMiddle += Math.abs(u - 20) <= 3 ? 1 : 0;
            triangleMiddle += Math.abs(t - 20) <= 3 ? 1 : 0;
            trapezoidEdge += z <= 11 ? 1 : 0;
        }
        assertTrue(triangleMiddle > uniformMiddle * 1.4, "triangle clusters in the middle");
        assertTrue(trapezoidEdge < 20000 * 0.05, "trapezoid thins out at the ends");
    }

    @Test
    void refusesNonsenseSpecs() {
        var height = new HeightDistribution.Uniform(0, 10);
        assertThrows(IllegalArgumentException.class, () -> new OreSpec("x", "a", "b", 0, 1, height, 0));
        assertThrows(IllegalArgumentException.class, () -> new OreSpec("x", "a", "b", 4, -1, height, 0));
        assertThrows(IllegalArgumentException.class, () -> new OreSpec("x", "a", "b", 4, 1, height, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new HeightDistribution.Triangle(10, 5));
    }
}
