package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import io.github.tst_038.minigami.paper.mode.TeamSetup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   - { x: 9.5, y: 64, z: 0.5, team: red }
 * waiting-spot: { x: 0.5, y: 70, z: 0.5 }
 * regions:
 *   hill: { from: { x: -3, y: 64, z: -3 }, to: { x: 3, y: 67, z: 3 } }
 * </pre>
 *
 * Yaw, pitch, the spawns, the waiting spot and the regions are optional. A spawn with a team is only
 * used by that team in modes with teams; spawns without one are for everyone. A region is the box of
 * blocks between its two corners, both included.
 */
public final class ArenaLoader {

    /** As far as a world reaches in Minecraft, in blocks. */
    private static final int WORLD_BORDER = 30_000_000;
    private static final int MIN_Y = -2048;
    private static final int MAX_Y = 2047;

    private ArenaLoader() {
    }

    /** Looks up worlds, so run it on the server thread. */
    public static YamlFiles.Loaded<Arena> load(YamlFiles files, Function<String, World> worlds) {
        return files.parse(Arena::id, (id, config) -> parse(id, config, worlds));
    }

    private static Arena parse(String id, ConfigReader config, Function<String, World> worlds) {
        var world = ConfigLocations.world(config, "world", worlds);
        var spawns = config.has("spawns")
                ? config.sections("spawns").stream().map(spawn -> spawn(spawn, world)).toList()
                : List.<Arena.Spawn>of();
        config.suggest("waiting-spot", List.of(ConfigLocations.HERE));
        var waitingSpot = config.has("waiting-spot")
                ? Optional.of(ConfigLocations.read(config.section("waiting-spot"), world))
                : Optional.<Location>empty();
        return new Arena(id, world, spawns, waitingSpot, regions(config, world));
    }

    private static Map<String, Region> regions(ConfigReader config, World world) {
        var regions = new LinkedHashMap<String, Region>();
        if (!config.has("regions")) {
            return regions;
        }
        var section = config.section("regions");
        for (String name : section.keys()) {
            var region = section.section(name);
            if (!IdRegistry.isValid(name)) {
                section.problem(name, "can't be a region name: use lower case letters, digits and dashes");
                region.keys();
                continue;
            }
            var from = region.section("from");
            var to = region.section("to");
            regions.put(name, Region.between(name, world, coordinate(from, "x"), height(from), coordinate(from, "z"),
                    coordinate(to, "x"), height(to), coordinate(to, "z")));
        }
        return regions;
    }

    private static int coordinate(ConfigReader corner, String key) {
        return corner.integer(key, -WORLD_BORDER, WORLD_BORDER);
    }

    private static int height(ConfigReader corner) {
        return corner.integer("y", MIN_Y, MAX_Y);
    }

    private static Arena.Spawn spawn(ConfigReader config, World world) {
        var location = ConfigLocations.read(config, world);
        config.suggest("team", TeamSetup.IDS);
        if (!config.has("team")) {
            return new Arena.Spawn(location, Optional.empty());
        }
        var team = config.string("team");
        if (!TeamSetup.IDS.contains(team)) {
            config.problem("team", "unknown team '" + team + "'; teams: " + String.join(", ", TeamSetup.IDS));
        }
        return new Arena.Spawn(location, Optional.of(team));
    }
}
