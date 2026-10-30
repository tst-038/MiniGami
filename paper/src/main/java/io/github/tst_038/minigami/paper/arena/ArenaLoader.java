package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import io.github.tst_038.minigami.paper.game.TeamSetup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Turns {@code arenas/<id>/arena.yml} files into arenas; the folder also holds the arena's saved map:
 *
 * <pre>
 * world: world
 * spawns:
 *   - { x: 0.5, y: 64, z: 0.5, yaw: 90, pitch: 0 }
 *   - { x: 9.5, y: 64, z: 0.5, team: red }
 * waiting-spot: { x: 0.5, y: 70, z: 0.5 }
 * spectator-spot: { x: 0.5, y: 90, z: 0.5 }
 * regions:
 *   hill: { from: { x: -3, y: 64, z: -3 }, to: { x: 3, y: 67, z: 3 } }
 * map: { from: { x: -20, y: 60, z: -20 }, to: { x: 20, y: 80, z: 20 } }
 * </pre>
 *
 * Yaw, pitch, the spawns, the waiting spot, the spectator spot (where players who are out watch from), the
 * regions and the map are optional. A spawn with a team is only used by that team in games with teams; spawns
 * without one are for everyone. A region is the box of
 * blocks between its two corners, both included. {@code map} is the box the arena's saved map is
 * pasted back into; saving the map sets it.
 */
public final class ArenaLoader {

    /** An arena's own folder, {@code arenas/<id>/}, holds this file. */
    public static final String FILE = "arena.yml";
    /** Where an arena's file is inside {@code arenas/}. */
    public static final String LAYOUT = "%s/" + FILE;

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
        config.suggest("spectator-spot", List.of(ConfigLocations.HERE));
        var spectatorSpot = config.has("spectator-spot")
                ? Optional.of(ConfigLocations.read(config.section("spectator-spot"), world))
                : Optional.<Location>empty();
        var map = config.has("map") ? Optional.of(box("map", config.section("map"), world)) : Optional.<Region>empty();
        return new Arena(id, world, spawns, waitingSpot, spectatorSpot, regions(config, world), map);
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
            regions.put(name, box(name, region, world));
        }
        return regions;
    }

    private static Region box(String name, ConfigReader config, World world) {
        var from = config.section("from");
        var to = config.section("to");
        return Region.between(name, world, coordinate(from, "x"), height(from), coordinate(from, "z"),
                coordinate(to, "x"), height(to), coordinate(to, "z"));
    }

    private static int coordinate(ConfigReader corner, String key) {
        return corner.integer(key, -WORLD_BORDER, WORLD_BORDER);
    }

    private static int height(ConfigReader corner) {
        return corner.integer("y", MIN_Y, MAX_Y);
    }

    private static Arena.Spawn spawn(ConfigReader config, World world) {
        var location = ConfigLocations.read(config, world);
        // A role's id works too; roles belong to games, which are loaded after arenas.
        config.suggest("team", TeamSetup.IDS);
        if (!config.has("team")) {
            return new Arena.Spawn(location, Optional.empty());
        }
        var team = config.string("team");
        if (!TeamSetup.IDS.contains(team) && !IdRegistry.isValid(team)) {
            config.problem("team", "'" + team + "' is neither a team (" + String.join(", ", TeamSetup.IDS)
                    + ") nor a role id in lower case letters, digits and dashes");
        }
        return new Arena.Spawn(location, Optional.of(team));
    }
}
