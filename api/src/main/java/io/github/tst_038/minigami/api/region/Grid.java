package io.github.tst_038.minigami.api.region;

import io.github.tst_038.minigami.core.terrain.BlockPalette;
import io.github.tst_038.minigami.core.terrain.Volume;

/**
 * Blocks worked out in memory, to put into a box of the arena: every block of the box becomes the
 * volume's cell, air included, so what was there before is replaced; barrier blocks already there are kept.
 *
 * @param box where the lowest corner of the volume goes; must be as big as the volume
 */
public record Grid(Region box, Volume volume, BlockPalette palette) {

    public Grid {
        if (box.maxX() - box.minX() + 1 != volume.sizeX() || box.maxY() - box.minY() + 1 != volume.sizeY()
                || box.maxZ() - box.minZ() + 1 != volume.sizeZ()) {
            throw new IllegalArgumentException("Region '" + box.id() + "' is not the size of its " + volume.sizeX() + "x"
                    + volume.sizeY() + "x" + volume.sizeZ() + " volume");
        }
    }
}
