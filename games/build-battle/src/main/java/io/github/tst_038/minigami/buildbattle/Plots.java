package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.api.region.Region;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/** The plots of one match, each an arena region shared by the builders of one team, in the order they were handed out. */
final class Plots {

    /** @param team the team's id, or empty for a player without a team */
    record Plot(Region region, Optional<String> team, List<UUID> builders) {

        Plot {
            builders = List.copyOf(builders);
        }

        /**
         * Where builders start and voters look from: above the middle of the plot, on the first free
         * block over the floor, or at the top if the middle is built full.
         */
        Location start() {
            var world = region.world();
            int x = Math.floorDiv(region.minX() + region.maxX(), 2);
            int z = Math.floorDiv(region.minZ() + region.maxZ(), 2);
            for (int y = region.minY() + 1; y < region.maxY(); y++) {
                if (world.getBlockAt(x, y - 1, z).isSolid() && world.getBlockAt(x, y, z).isPassable()
                        && world.getBlockAt(x, y + 1, z).isPassable()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
            return new Location(world, x + 0.5, region.maxY(), z + 0.5);
        }
    }

    private final List<Plot> plots = new ArrayList<>();
    private final Map<UUID, Plot> byBuilder = new HashMap<>();

    void claim(Region region, Optional<String> team, List<UUID> builders) {
        var plot = new Plot(region, team, builders);
        plots.add(plot);
        builders.forEach(id -> byBuilder.put(id, plot));
    }

    List<Plot> all() {
        return List.copyOf(plots);
    }

    Optional<Plot> of(UUID builder) {
        return Optional.ofNullable(byBuilder.get(builder));
    }

    boolean contains(UUID builder, Location location) {
        return of(builder).filter(plot -> plot.region().contains(location)).isPresent();
    }
}
