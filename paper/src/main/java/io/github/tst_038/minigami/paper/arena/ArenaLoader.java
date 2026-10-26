package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import java.util.ArrayList;
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

    /** The arenas that are valid, and the problems of those that aren't. */
    public record Loaded(IdRegistry<Arena> arenas, List<ConfigProblem> problems) {
    }

    private ArenaLoader() {
    }

    /** Looks up worlds, so run it on the server thread. */
    public static Loaded load(YamlFiles files, Function<String, World> worlds) {
        var arenas = new IdRegistry<>(Arena::id);
        var problems = new ArrayList<>(files.problems());
        files.files().forEach((id, config) -> {
            var file = files.fileName(id);
            try {
                arenas.register(parse(id, ConfigReader.of(file, config), worlds));
            } catch (InvalidConfigException e) {
                problems.addAll(e.problems());
            } catch (IllegalArgumentException e) {
                problems.add(new ConfigProblem(file, "", e.getMessage()));
            }
        });
        return new Loaded(arenas, problems);
    }

    static Arena parse(String id, ConfigReader config, Function<String, World> worlds) throws InvalidConfigException {
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
        config.check();
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
