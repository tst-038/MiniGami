package io.github.tst_038.minigami.api.region;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * A named box of blocks in an arena, such as a hill to hold or a plot to build on. Both corners are
 * part of it. Use {@link #between} to make one from any two corners.
 */
public record Region(String id, World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Region {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("The minimum corner of region '" + id + "' lies beyond its maximum");
        }
    }

    /** The box spanned by two opposite corners, in either order. */
    public static Region between(String id, World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Region(id, world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * Whether the region belongs to a group of regions that an arena can have any number of: its name
     * is the group's, or starts with it and a dash, as {@code wall}, {@code wall-north} and {@code wall-2} do for {@code wall}.
     */
    public boolean isIn(String group) {
        return id.equals(group) || id.startsWith(group + "-");
    }

    /** Whether the block at this location is part of the region. */
    public boolean contains(Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return world.equals(location.getWorld()) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Where a player can be put down: above the middle of the region, on the first free block over the
     * floor, or at the top if the middle is built full.
     */
    public Location standingSpot() {
        int x = Math.floorDiv(minX + maxX, 2);
        int z = Math.floorDiv(minZ + maxZ, 2);
        for (int y = minY + 1; y < maxY; y++) {
            if (world.getBlockAt(x, y - 1, z).isSolid() && world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return new Location(world, x + 0.5, maxY, z + 0.5);
    }
}
