package io.github.tst_038.minigami.api.region;

import io.github.tst_038.minigami.api.config.ConfigReader;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A named box of blocks in an arena, such as a hill to hold or a plot to build on. Both corners are
 * part of it. Use {@link #between} to make one from any two corners.
 */
public record Region(String id, World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** As far as a world reaches in Minecraft, in blocks. */
    private static final int WORLD_BORDER = 30_000_000;
    private static final int MIN_Y = -2048;
    private static final int MAX_Y = 2047;

    public Region {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("The minimum corner of region '" + id + "' lies beyond its maximum");
        }
    }

    /** The scoreboard tag of what belongs to this region as its own, such as a mob admitted to a plot. */
    public String plotTag() {
        return "minigami-plot:" + id;
    }

    /** The box spanned by two opposite corners, in either order. */
    public static Region between(String id, World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Region(id, world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * Reads a region from a config section, {@code { from: { x: -3, y: 64, z: -3 }, to: { x: 3, y: 67, z: 3 } }}; the
     * corners may be given in either order.
     */
    public static Region read(String id, ConfigReader config, World world) {
        var from = config.section("from");
        var to = config.section("to");
        return between(id, world, coordinate(from, "x"), height(from), coordinate(from, "z"),
                coordinate(to, "x"), height(to), coordinate(to, "z"));
    }

    private static int coordinate(ConfigReader corner, String key) {
        return corner.integer(key, -WORLD_BORDER, WORLD_BORDER);
    }

    private static int height(ConfigReader corner) {
        return corner.integer("y", MIN_Y, MAX_Y);
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
        return java.util.Objects.equals(world, location.getWorld()) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Whether someone can stand in the region: the block at this location is part of it or the one right above it, where feet rest on its top layer. */
    public boolean containsStanding(Location location) {
        return contains(location) || contains(location.clone().subtract(0, 1, 0));
    }

    /**
     * Where a player can be put down: above the middle of the region, on the highest free block over a
     * floor (so never in a cave below a region that spans the world's height), or at the top if the middle
     * is built full.
     */
    public Location standingSpot() {
        int x = Math.floorDiv(minX + maxX, 2);
        int z = Math.floorDiv(minZ + maxZ, 2);
        for (int y = maxY - 1; y > minY; y--) {
            if (world.getBlockAt(x, y - 1, z).isSolid() && world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return new Location(world, x + 0.5, maxY, z + 0.5);
    }
}
