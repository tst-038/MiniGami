package io.github.tst_038.minigami.api.region;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.core.terrain.BlockPalette;
import io.github.tst_038.minigami.core.terrain.Volume;
import org.junit.jupiter.api.Test;

class GridTest {

    @Test
    void theBoxMustBeAsBigAsTheVolume() {
        var volume = new Volume(4, 3, 5);
        var palette = new BlockPalette();
        assertDoesNotThrow(() -> new Grid(Region.between("a", null, 10, 0, 10, 13, 2, 14), volume, palette));
        assertThrows(IllegalArgumentException.class, () -> new Grid(Region.between("a", null, 10, 0, 10, 14, 2, 14), volume, palette));
    }
}
