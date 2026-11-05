package io.github.tst_038.minigami.core.terrain;

/**
 * A box of blocks with no world behind it: one palette number per cell, x fastest, then z, then y.
 * It knows its size, not where it will be placed.
 */
public final class Volume {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final short[] cells;

    public Volume(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("A volume needs a size of at least 1 in every direction");
        }
        if ((long) sizeX * sizeY * sizeZ > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A volume of " + sizeX + "x" + sizeY + "x" + sizeZ + " is too large");
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = new short[sizeX * sizeY * sizeZ];
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int cellCount() {
        return cells.length;
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    public short get(int x, int y, int z) {
        return cells[index(x, y, z)];
    }

    public void set(int x, int y, int z, short block) {
        cells[index(x, y, z)] = block;
    }

    /** The cell at this position, or {@code outside} when it lies beyond the box. */
    public short getOr(int x, int y, int z, short outside) {
        return contains(x, y, z) ? get(x, y, z) : outside;
    }

    /** How many cells hold this block. */
    public int count(short block) {
        int found = 0;
        for (var cell : cells) {
            if (cell == block) {
                found++;
            }
        }
        return found;
    }

    /** The cells in storage order, as a copy; for placing in the world and for comparing in tests. */
    public short[] cells() {
        return cells.clone();
    }

    public int index(int x, int y, int z) {
        if (!contains(x, y, z)) {
            throw new IndexOutOfBoundsException("(" + x + ", " + y + ", " + z + ") is outside " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        return (y * sizeZ + z) * sizeX + x;
    }
}
