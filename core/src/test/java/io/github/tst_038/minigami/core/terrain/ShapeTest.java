package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeTest {

    private static final int SIZE = 64;
    private static final int HEIGHT = 80;

    private final BlockPalette palette = new BlockPalette();

    private Ground shape(Volume volume, List<Anchor> anchors, long seed) {
        return Shape.apply(volume, palette, TestStyles.SHAPE, anchors, seed);
    }

    @Test
    void sameSeedGivesTheSameLand() {
        var first = new Volume(SIZE, HEIGHT, SIZE);
        var second = new Volume(SIZE, HEIGHT, SIZE);
        shape(first, List.of(), 7);
        shape(second, List.of(), 7);
        assertArrayEquals(first.cells(), second.cells());

        var other = new Volume(SIZE, HEIGHT, SIZE);
        shape(other, List.of(), 8);
        assertFalse(java.util.Arrays.equals(first.cells(), other.cells()));
    }

    @Test
    void everyCellIsWrittenAndColumnsAreSolidThenAirOrWater() {
        var volume = new Volume(SIZE, HEIGHT, SIZE);
        var ground = shape(volume, List.of(), 3);
        short air = palette.index("minecraft:air");
        short water = palette.index("minecraft:water");

        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int top = ground.height(x, z);
                assertTrue(top >= 5 && top <= HEIGHT - 5, "height " + top);
                for (int y = 0; y <= top; y++) {
                    assertNotEquals(air, volume.get(x, y, z));
                    assertNotEquals(water, volume.get(x, y, z));
                }
                for (int y = top + 1; y < HEIGHT; y++) {
                    short expected = y <= TestStyles.SHAPE.seaLevel() ? water : air;
                    assertEquals(expected, volume.get(x, y, z), "at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void floorIsBedrockWithARaggedTop() {
        var volume = new Volume(SIZE, HEIGHT, SIZE);
        shape(volume, List.of(), 3);
        short bedrock = palette.index("minecraft:bedrock");

        assertEquals(SIZE * SIZE, countAtLayer(volume, 0, bedrock));
        assertTrue(countAtLayer(volume, 2, bedrock) > 0 && countAtLayer(volume, 2, bedrock) < SIZE * SIZE);
        assertEquals(0, countAtLayer(volume, 5, bedrock));
    }

    @Test
    void deepslateLiesLowAndStoneHigh() {
        var volume = new Volume(SIZE, HEIGHT, SIZE);
        shape(volume, List.of(), 3);
        short deepslate = palette.index("minecraft:deepslate");
        short stone = palette.index("minecraft:stone");

        assertEquals(0, countAtLayer(volume, 13, deepslate));
        assertEquals(SIZE * SIZE, countAtLayer(volume, 5, deepslate));
        assertTrue(countAtLayer(volume, 10, stone) > 0 && countAtLayer(volume, 10, deepslate) > 0);
    }

    @Test
    void anchorsStandOnFlatDryGroundWithRoomAbove() {
        var volume = new Volume(SIZE, HEIGHT, SIZE);
        var anchors = List.of(new Anchor(10, 45, 10), new Anchor(50, 45, 50));
        var ground = shape(volume, anchors, 11);
        short air = palette.index("minecraft:air");

        for (var anchor : anchors) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dx = -5; dx <= 5; dx++) {
                    if (Math.hypot(dx, dz) > 5) {
                        continue;
                    }
                    int x = anchor.x() + dx;
                    int z = anchor.z() + dz;
                    assertEquals(anchor.y() - 1, ground.height(x, z), "pad ground at " + x + "," + z);
                    assertEquals(air, volume.get(x, anchor.y(), z));
                    assertEquals(air, volume.get(x, anchor.y() + 1, z));
                }
            }
        }
    }

    @Test
    void padsBlendIntoTheHillsInsteadOfCliffs() {
        var volume = new Volume(SIZE, HEIGHT, SIZE);
        var ground = shape(volume, List.of(new Anchor(32, 36, 32)), 5);
        for (int x = 33; x < SIZE; x++) {
            assertTrue(Math.abs(ground.height(x, 32) - ground.height(x - 1, 32)) <= 8, "step at x=" + x);
        }
    }

    @Test
    void refusesNonsenseSpecs() {
        assertThrows(IllegalArgumentException.class, () -> new ShapeSpec.Wave(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShapeSpec.Wave(1, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShapeSpec(TestStyles.SHAPE.height(), -1, "a", "b", "c", 1, 1));
    }

    private static int countAtLayer(Volume volume, int y, short block) {
        int found = 0;
        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                if (volume.get(x, y, z) == block) {
                    found++;
                }
            }
        }
        return found;
    }
}
