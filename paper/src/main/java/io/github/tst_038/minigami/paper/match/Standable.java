package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.core.spawn.Spread;
import io.github.tst_038.minigami.api.region.Region;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.bukkit.Location;

/** Places a player can be put down on: solid ground below and room to stand, which games that break the floor take away. */
final class Standable {

    private Standable() {
    }

    /** The standable ones of {@code spots}, or all of them if a game broke the floor under every one. */
    static List<Location> among(List<Location> spots) {
        var standable = spots.stream().filter(Standable::isStandable).toList();
        return standable.isEmpty() ? spots : standable;
    }

    /**
     * The standable places of the region by the height of their floor, lowest first. One pass over the region, one block
     * read per block, so it costs about as much as the region has blocks; empty if the whole region is gone.
     */
    static SortedMap<Integer, List<Location>> floors(Region region) {
        var floors = new TreeMap<Integer, List<Location>>();
        var world = region.world();
        // Per column, one block read each from just under the region to two over it: players stand on its top layer, so there is room above it.
        int height = region.maxY() - region.minY() + 4;
        var solid = new boolean[height];
        var passable = new boolean[height];
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int i = 0; i < height; i++) {
                    var block = world.getBlockAt(x, region.minY() - 1 + i, z);
                    solid[i] = block.isSolid();
                    passable[i] = block.isPassable();
                }
                for (int i = 1; i < height - 1; i++) {
                    if (solid[i - 1] && passable[i] && passable[i + 1]) {
                        int y = region.minY() - 1 + i;
                        floors.computeIfAbsent(y, key -> new ArrayList<>()).add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                }
            }
        }
        return floors;
    }

    static Spread.Point point(Location location) {
        return new Spread.Point(location.getX(), location.getY(), location.getZ());
    }

    private static boolean isStandable(Location spot) {
        return spot.clone().subtract(0, 0.1, 0).getBlock().isSolid() && spot.getBlock().isPassable() && spot.clone().add(0, 1, 0).getBlock().isPassable();
    }
}
