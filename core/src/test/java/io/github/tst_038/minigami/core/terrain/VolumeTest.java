package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class VolumeTest {

    @Test
    void storesAndCountsCells() {
        var volume = new Volume(3, 4, 5);
        volume.set(2, 3, 4, (short) 7);
        volume.set(0, 0, 0, (short) 7);

        assertEquals(7, volume.get(2, 3, 4));
        assertEquals(2, volume.count((short) 7));
        assertEquals(60 - 2, volume.count((short) 0));
        assertEquals(60, volume.cellCount());
    }

    @Test
    void everyCellHasItsOwnIndex() {
        var volume = new Volume(3, 4, 5);
        var seen = new HashSet<Integer>();
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 3; x++) {
                    seen.add(volume.index(x, y, z));
                }
            }
        }
        assertEquals(60, seen.size());
        assertEquals(1, volume.index(1, 0, 0));
        assertEquals(3, volume.index(0, 0, 1));
        assertEquals(15, volume.index(0, 1, 0));
    }

    @Test
    void refusesCellsOutsideTheBox() {
        var volume = new Volume(2, 2, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> volume.get(2, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> volume.set(0, -1, 0, (short) 1));
        assertFalse(volume.contains(0, 0, 2));
        assertTrue(volume.contains(1, 1, 1));
        assertEquals(9, volume.getOr(5, 5, 5, (short) 9));
    }

    @Test
    void refusesEmptyOrHugeBoxes() {
        assertThrows(IllegalArgumentException.class, () -> new Volume(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Volume(100000, 100000, 100000));
    }

    @Test
    void cellsAreACopy() {
        var volume = new Volume(1, 1, 2);
        volume.cells()[0] = 5;
        assertEquals(0, volume.get(0, 0, 0));
    }

    @Test
    void paletteKeepsAirAtZeroAndReusesNumbers() {
        var palette = new BlockPalette();
        short stone = palette.index("minecraft:stone");
        assertEquals(0, palette.index(BlockPalette.AIR));
        assertEquals(stone, palette.index("minecraft:stone"));
        assertEquals("minecraft:stone", palette.block(stone));
        assertEquals(2, palette.size());
    }
}
