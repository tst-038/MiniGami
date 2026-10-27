package io.github.tst_038.minigami.buildbattle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/**
 * The plot of each builder in one match: a square of {@code radius} blocks around the spawn they
 * started on, from the floor block under it up to twice the radius above.
 */
final class Plots {

    private final int radius;
    private final Map<UUID, Location> centers = new LinkedHashMap<>();

    Plots(int radius) {
        this.radius = radius;
    }

    void claim(UUID builder, Location center) {
        centers.put(builder, center.clone());
    }

    List<UUID> builders() {
        return List.copyOf(centers.keySet());
    }

    Optional<Location> center(UUID builder) {
        return Optional.ofNullable(centers.get(builder)).map(Location::clone);
    }

    boolean contains(UUID builder, Location location) {
        var center = centers.get(builder);
        if (center == null || center.getWorld() != location.getWorld()) {
            return false;
        }
        var dy = location.getBlockY() - center.getBlockY();
        return Math.abs(location.getBlockX() - center.getBlockX()) <= radius
                && Math.abs(location.getBlockZ() - center.getBlockZ()) <= radius
                && dy >= -1 && dy <= 2 * radius;
    }
}
