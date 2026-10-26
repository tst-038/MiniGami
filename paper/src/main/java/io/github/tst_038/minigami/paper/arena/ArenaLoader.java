package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Turns {@code arenas/<id>.yml} files into arenas:
 *
 * <pre>
 * world: world
 * spawns:
 *   - { x: 0.5, y: 64, z: 0.5, yaw: 90, pitch: 0 }
 * waiting-spot: { x: 0.5, y: 70, z: 0.5 }
 * </pre>
 *
 * Yaw, pitch, the spawns and the waiting spot are optional.
 */
public final class ArenaLoader {

    private ArenaLoader() {
    }

    /** Looks up worlds, so run it on the server thread. */
    public static YamlFiles.Loaded<Arena> load(YamlFiles files, Function<String, World> worlds) {
        return files.parse(Arena::id, (id, config) -> parse(id, config, worlds));
    }

    private static Arena parse(String id, ConfigReader config, Function<String, World> worlds) {
        var worldName = config.string("world");
        var world = worldName.isEmpty() ? null : worlds.apply(worldName);
        if (!worldName.isEmpty() && world == null) {
            config.problem("world", "no loaded world '" + worldName + "'");
        }
        var spawns = config.has("spawns")
                ? config.sections("spawns").stream().map(spawn -> location(spawn, world)).toList()
                : List.<Location>of();
        var waitingSpot = config.has("waiting-spot")
                ? Optional.of(location(config.section("waiting-spot"), world))
                : Optional.<Location>empty();
        return new Arena(id, spawns, waitingSpot);
    }

    private static Location location(ConfigReader config, World world) {
        var x = config.decimal("x");
        var y = config.decimal("y");
        var z = config.decimal("z");
        var yaw = config.has("yaw") ? (float) config.decimal("yaw") : 0;
        var pitch = config.has("pitch") ? (float) config.decimal("pitch") : 0;
        return new Location(world, x, y, z, yaw, pitch);
    }
}
