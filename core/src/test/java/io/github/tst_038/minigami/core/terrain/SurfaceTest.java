package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SurfaceTest {

    private static final int SIZE = 96;
    private static final int HEIGHT = 80;

    private final BlockPalette palette = new BlockPalette();
    private final Volume volume = new Volume(SIZE, HEIGHT, SIZE);
    private final Ground ground = Shape.apply(volume, palette, TestStyles.SHAPE, List.of(), 21);

    {
        Surface.apply(volume, palette, TestStyles.SHAPE, TestStyles.SURFACE, ground, 21);
    }

    private String blockAt(int x, int y, int z) {
        return palette.block(volume.get(x, y, z));
    }

    @Test
    void highLandIsGrassOverDirt() {
        int checked = 0;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int top = ground.height(x, z);
                if (top > TestStyles.SHAPE.seaLevel() + 3) {
                    checked++;
                    assertEquals("minecraft:grass_block", blockAt(x, top, z));
                    assertEquals("minecraft:dirt", blockAt(x, top - 1, z));
                    assertEquals("minecraft:dirt", blockAt(x, top - 2, z));
                    assertEquals("minecraft:stone", blockAt(x, top - 6, z));
                }
            }
        }
        assertTrue(checked > 100, "the test land has hills");
    }

    @Test
    void shoreIsSandHeldUpBySandstone() {
        int checked = 0;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int top = ground.height(x, z);
                if (Math.abs(top - TestStyles.SHAPE.seaLevel()) <= 2) {
                    checked++;
                    assertEquals("minecraft:sand", blockAt(x, top, z));
                    boolean held = false;
                    for (int y = top - 3; y >= top - 6; y--) {
                        held |= blockAt(x, y, z).equals("minecraft:sandstone");
                    }
                    assertTrue(held, "sandstone under the sand at " + x + "," + z);
                }
            }
        }
        assertTrue(checked > 0, "the test land has a shore");
    }

    @Test
    void deepWaterHasAMixedLakebedAndNeverGrass() {
        var kinds = new java.util.HashSet<String>();
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int top = ground.height(x, z);
                if (TestStyles.SHAPE.seaLevel() - top > 2) {
                    kinds.add(blockAt(x, top, z));
                }
            }
        }
        assertTrue(!kinds.isEmpty(), "the test land has a lake");
        assertTrue(java.util.Set.of("minecraft:gravel", "minecraft:sand", "minecraft:clay").containsAll(kinds), kinds.toString());
    }

    @Test
    void sameSeedGivesTheSameSurface() {
        var palette2 = new BlockPalette();
        var volume2 = new Volume(SIZE, HEIGHT, SIZE);
        var ground2 = Shape.apply(volume2, palette2, TestStyles.SHAPE, List.of(), 21);
        Surface.apply(volume2, palette2, TestStyles.SHAPE, TestStyles.SURFACE, ground2, 21);
        assertArrayEquals(volume.cells(), volume2.cells());
    }
}
