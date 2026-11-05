package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CavesTest {

    private static final int SIZE = 64;
    private static final int HEIGHT = 80;
    private static final CaveSpec SPEC = new CaveSpec(0.1, 14, 4, 10);

    private final BlockPalette palette = new BlockPalette();
    private final Volume volume = new Volume(SIZE, HEIGHT, SIZE);
    private final Ground ground = Shape.apply(volume, palette, TestStyles.SHAPE, List.of(), 4);
    private final short air = palette.index(BlockPalette.AIR);
    private final short lava = palette.index("minecraft:lava");
    private final short bedrock = palette.index(TestStyles.SHAPE.floor());

    private void carve(Volume target, BlockPalette from, Ground over) {
        Caves.apply(target, from, TestStyles.SHAPE, SPEC, over, 4);
    }

    @Test
    void cutsTunnelsButOnlyUnderneathTheSurface() {
        var before = volume.cells();
        carve(volume, palette, ground);
        int carved = 0;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    short now = volume.get(x, y, z);
                    boolean changed = now != before[volume.index(x, y, z)];
                    if (changed) {
                        carved++;
                        assertTrue(now == air || now == lava);
                        assertTrue(y >= 5 && y <= ground.height(x, z) - SPEC.minDepth(), "carved at y=" + y);
                    }
                }
            }
        }
        double share = (double) carved / volume.cellCount();
        assertTrue(share > 0.002 && share < 0.25, "carved share " + share);
    }

    @Test
    void lavaFillsOnlyTheDeepTunnels() {
        carve(volume, palette, ground);
        int lavaCells = 0;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (volume.get(x, y, z) == lava) {
                        lavaCells++;
                        assertTrue(y <= SPEC.lavaLevel());
                    }
                }
            }
        }
        assertTrue(lavaCells > 0, "some tunnel dips below the lava level");
    }

    @Test
    void floorStaysBedrockAndSameSeedGivesSameCaves() {
        carve(volume, palette, ground);
        assertEquals(SIZE * SIZE, count(volume, 0, bedrock));

        var palette2 = new BlockPalette();
        var volume2 = new Volume(SIZE, HEIGHT, SIZE);
        carve(volume2, palette2, Shape.apply(volume2, palette2, TestStyles.SHAPE, List.of(), 4));
        assertArrayEquals(volume.cells(), volume2.cells());
    }

    @Test
    void refusesNonsenseSpecs() {
        assertThrows(IllegalArgumentException.class, () -> new CaveSpec(0, 10, 3, 5));
        assertThrows(IllegalArgumentException.class, () -> new CaveSpec(0.1, 10, 0, 5));
    }

    private static int count(Volume volume, int y, short block) {
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
