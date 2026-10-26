package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
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
        var world = ConfigLocations.world(config, "world", worlds);
        var spawns = config.has("spawns")
                ? config.sections("spawns").stream().map(spawn -> ConfigLocations.read(spawn, world)).toList()
                : List.<Location>of();
        var waitingSpot = config.has("waiting-spot")
                ? Optional.of(ConfigLocations.read(config.section("waiting-spot"), world))
                : Optional.<Location>empty();
        return new Arena(id, world, spawns, waitingSpot);
    }
}
