package io.github.tst_038.minigami.paper.config;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;

/** Locations in config: {@code { x: 0.5, y: 64, z: 0.5, yaw: 90, pitch: 0 }}, yaw and pitch optional. */
public final class ConfigLocations {

    private ConfigLocations() {
    }

    /** A loaded world by name; a problem if there is none. Looks up worlds, so run it on the server thread. */
    public static World world(ConfigReader config, String key, Function<String, World> worlds) {
        var name = config.string(key);
        var world = name.isEmpty() ? null : worlds.apply(name);
        if (!name.isEmpty() && world == null) {
            config.problem(key, "no loaded world '" + name + "'");
        }
        return world;
    }

    public static Location read(ConfigReader config, World world) {
        var x = config.decimal("x");
        var y = config.decimal("y");
        var z = config.decimal("z");
        var yaw = config.has("yaw") ? (float) config.decimal("yaw") : 0;
        var pitch = config.has("pitch") ? (float) config.decimal("pitch") : 0;
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** A location that names its own world: {@code { world: world, x: 0.5, ... }}. */
    public static Location readWithWorld(ConfigReader config, Function<String, World> worlds) {
        return read(config, world(config, "world", worlds));
    }
}
