package io.github.tst_038.minigami.paper.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;

/** Writing locations into config, the other way round from {@link io.github.tst_038.minigami.api.config.ConfigLocations}. */
public final class LocationConfig {

    /** Typed as a location's value in a command: the sender's location. */
    public static final String HERE = "here";

    private LocationConfig() {
    }

    /** The other way round from {@code ConfigLocations.read}, rounded so the file stays readable. */
    public static Map<String, Object> write(Location location) {
        var map = new LinkedHashMap<String, Object>();
        map.put("x", round(location.getX(), 100));
        map.put("y", round(location.getY(), 100));
        map.put("z", round(location.getZ(), 100));
        map.put("yaw", round(location.getYaw(), 10));
        map.put("pitch", round(location.getPitch(), 10));
        return map;
    }

    /** The other way round from {@code ConfigLocations.readWithWorld}. */
    public static Map<String, Object> writeWithWorld(Location location) {
        var map = new LinkedHashMap<String, Object>();
        map.put("world", location.getWorld().getName());
        map.putAll(write(location));
        return map;
    }

    private static double round(double value, int scale) {
        return Math.round(value * scale) / (double) scale;
    }
}
