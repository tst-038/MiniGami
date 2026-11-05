package io.github.tst_038.minigami.features.terrain;

import io.github.tst_038.minigami.api.region.Grid;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.terrain.Anchor;
import io.github.tst_038.minigami.core.terrain.AreaRequest;
import io.github.tst_038.minigami.core.terrain.GeneratedArea;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bukkit.Location;

/** Translates between the arena's regions and spawns and the world-free areas the terrain generator works with. */
public final class Areas {

    private Areas() {
    }

    /**
     * Trims each region from below so that the style's ground height lands on its spawns: land is shaped
     * for the bottom of the area, so a region spanning the world's height (-64 up) would otherwise sink the land
     * far under the spawns and pull a spike up to them. Regions without spawns are kept as they are.
     */
    public static List<Region> windows(List<Region> regions, List<Location> spawns, int groundHeight) {
        return regions.stream().map(region -> {
            var lowest = spawns.stream().filter(region::contains).mapToInt(Location::getBlockY).min();
            if (lowest.isEmpty()) {
                return region;
            }
            int bottom = Math.max(region.minY(), lowest.getAsInt() - groundHeight - 1);
            return new Region(region.id(), region.world(), region.minX(), bottom, region.minZ(), region.maxX(), region.maxY(), region.maxZ());
        }).toList();
    }

    /** One area per region, sized like it; each spawn inside a region becomes an anchor of it, in the region's own coordinates. */
    public static List<AreaRequest> requests(List<Region> regions, List<Location> spawns) {
        return regions.stream().map(region -> new AreaRequest(region.id(),
                region.maxX() - region.minX() + 1, region.maxY() - region.minY() + 1, region.maxZ() - region.minZ() + 1,
                spawns.stream().filter(region::contains)
                        .map(spawn -> new Anchor(spawn.getBlockX() - region.minX(), spawn.getBlockY() - region.minY(), spawn.getBlockZ() - region.minZ()))
                        .toList())).toList();
    }

    /** The generated areas as grids that fill the regions they were made for. */
    public static List<Grid> grids(List<Region> regions, List<GeneratedArea> areas) {
        Map<String, Region> byId = regions.stream().collect(Collectors.toMap(Region::id, Function.identity()));
        return areas.stream().map(area -> new Grid(byId.get(area.request().id()), area.volume(), area.palette())).toList();
    }
}
