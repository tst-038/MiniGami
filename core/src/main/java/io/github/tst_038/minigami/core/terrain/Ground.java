package io.github.tst_038.minigami.core.terrain;

/**
 * The height of the land in every column of an area: the y of its highest solid block, before
 * trees and the like. Later stages read it to know where the surface is.
 */
public record Ground(int sizeX, int sizeZ, int[] heights) {

    public Ground {
        if (heights.length != sizeX * sizeZ) {
            throw new IllegalArgumentException("A ground of " + sizeX + "x" + sizeZ + " needs " + sizeX * sizeZ + " heights");
        }
    }

    public int height(int x, int z) {
        return heights[z * sizeX + x];
    }

    void set(int x, int z, int height) {
        heights[z * sizeX + x] = height;
    }
}
