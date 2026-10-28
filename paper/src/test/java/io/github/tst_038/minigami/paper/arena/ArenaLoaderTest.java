package io.github.tst_038.minigami.paper.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.region.Region;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ArenaLoaderTest {

    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "world";
                case "equals" -> proxy == args[0];
                case "hashCode" -> 1;
                default -> throw new UnsupportedOperationException(method.getName());
            });

    private YamlFiles.Loaded<Arena> load(String id, String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        var files = new YamlFiles("arenas", ArenaLoader.LAYOUT, Map.of(id, config), List.of());
        return ArenaLoader.load(files, name -> name.equals("world") ? world : null);
    }

    private static List<String> problems(YamlFiles.Loaded<Arena> loaded) {
        return loaded.problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void loadsSpawnsAndWaitingSpot() throws Exception {
        var loaded = load("winter", """
                world: world
                spawns:
                  - { x: 0.5, y: 64, z: 0.5, yaw: 90, pitch: 10 }
                  - { x: 5, y: 64, z: 5 }
                waiting-spot: { x: 1, y: 70, z: 1 }
                """);

        assertEquals(List.of(), problems(loaded));
        var arena = loaded.entries().find("winter").orElseThrow();
        assertEquals(List.of(new Location(world, 0.5, 64, 0.5, 90, 10), new Location(world, 5, 64, 5)), arena.spawnsFor(Optional.empty()));
        assertEquals(Optional.of(new Location(world, 1, 70, 1)), arena.waitingSpot());
        assertTrue(arena.isPlayable());
    }

    @Test
    void arenaWithOnlyAWorldIsValidButNotPlayable() throws Exception {
        var loaded = load("new", "world: world");

        var arena = loaded.entries().find("new").orElseThrow();
        assertFalse(arena.isPlayable());
        assertEquals(Optional.empty(), arena.waitingSpot());
    }

    @Test
    void invalidArenaIsLeftOutWithAllItsProblems() throws Exception {
        var loaded = load("winter", """
                world: wintr
                spawns:
                  - { x: 1, y: 64 }
                """);

        assertTrue(loaded.entries().ids().isEmpty());
        assertEquals(List.of(
                "arenas/winter/arena.yml: world: no loaded world 'wintr'",
                "arenas/winter/arena.yml: spawns[0].z: is missing"), problems(loaded));
    }

    @Test
    void loadsRegionsInFileOrderWithCornersInAnyOrder() throws Exception {
        var loaded = load("hills", """
                world: world
                regions:
                  top: { from: { x: 3, y: 70, z: 3 }, to: { x: -3, y: 64, z: -3 } }
                  base: { from: { x: 10, y: 60, z: 10 }, to: { x: 10, y: 60, z: 10 } }
                """);

        assertEquals(List.of(), problems(loaded));
        var arena = loaded.entries().find("hills").orElseThrow();
        assertEquals(List.of("top", "base"), List.copyOf(arena.regions().keySet()));
        assertEquals(new Region("top", world, -3, 64, -3, 3, 70, 3), arena.regions().get("top"));
        assertEquals(new Region("base", world, 10, 60, 10, 10, 60, 10), arena.regions().get("base"));
    }

    @Test
    void loadsTheMapBox() throws Exception {
        var loaded = load("hills", """
                world: world
                map: { from: { x: 14, y: 104, z: 1 }, to: { x: 3, y: 100, z: -7 } }
                """);

        assertEquals(List.of(), problems(loaded));
        assertEquals(Optional.of(new Region("map", world, 3, 100, -7, 14, 104, 1)), loaded.entries().find("hills").orElseThrow().map());
        assertEquals(Optional.empty(), load("plain", "world: world").entries().find("plain").orElseThrow().map());
    }

    @Test
    void reportsBadRegions() throws Exception {
        var loaded = load("hills", """
                world: world
                regions:
                  Top: { from: { x: 0, y: 64, z: 0 }, to: { x: 1, y: 64, z: 1 } }
                  base: { from: { x: 0, y: 64 }, to: { x: 1, y: 9999, z: 1, w: 2 } }
                """);

        assertTrue(loaded.entries().ids().isEmpty());
        assertEquals(List.of(
                "arenas/hills/arena.yml: regions.Top: can't be a region name: use lower case letters, digits and dashes",
                "arenas/hills/arena.yml: regions.base.from.z: is missing",
                "arenas/hills/arena.yml: regions.base.to.y: must be between -2048 and 2047",
                "arenas/hills/arena.yml: regions.base.to.w: unknown key"), problems(loaded));
    }

    @Test
    void fileNameMustBeAValidId() throws Exception {
        var loaded = load("Winter", "world: world");

        assertEquals(List.of("arenas/Winter/arena.yml: Invalid id 'Winter': use lower case letters, digits and dashes"), problems(loaded));
    }

    @Test
    void handedOutLocationsCanBeChangedSafely() throws Exception {
        var arena = load("winter", "world: world\nspawns: [{ x: 0, y: 64, z: 0 }]").entries().find("winter").orElseThrow();

        arena.spawns().getFirst().location().add(0, 100, 0);
        arena.spawnsFor(Optional.empty()).getFirst().add(0, 100, 0);

        assertEquals(64, arena.spawns().getFirst().location().getY());
    }

    @Test
    void aTeamStartsAtItsOwnSpawnsElseTheSharedOnesElseAnywhere() throws Exception {
        var loaded = load("winter", """
                world: world
                spawns:
                  - { x: 1, y: 64, z: 1, team: red }
                  - { x: 2, y: 64, z: 2 }
                  - { x: 3, y: 64, z: 3, team: red }
                """);
        var onlyTeams = load("summer", "world: world\nspawns: [{ x: 1, y: 64, z: 1, team: red }]");

        assertEquals(List.of(), problems(loaded));
        var arena = loaded.entries().find("winter").orElseThrow();
        assertEquals(List.of(new Location(world, 1, 64, 1), new Location(world, 3, 64, 3)), arena.spawnsFor(Optional.of("red")));
        assertEquals(List.of(new Location(world, 2, 64, 2)), arena.spawnsFor(Optional.of("blue")));
        assertEquals(List.of(new Location(world, 2, 64, 2)), arena.spawnsFor(Optional.empty()));
        assertEquals(List.of(new Location(world, 1, 64, 1)),
                onlyTeams.entries().find("summer").orElseThrow().spawnsFor(Optional.of("blue")));
    }

    @Test
    void aSpawnsTeamIsATeamColourOrARoleId() throws Exception {
        var role = load("winter", "world: world\nspawns: [{ x: 1, y: 64, z: 1, team: seeker }]");
        var invalid = load("winter", "world: world\nspawns: [{ x: 1, y: 64, z: 1, team: Seeker }]");

        assertEquals(List.of(), problems(role));
        assertEquals(List.of(new Location(world, 1, 64, 1)), role.entries().find("winter").orElseThrow().spawnsFor(Optional.of("seeker")));
        assertEquals(List.of("arenas/winter/arena.yml: spawns[0].team: 'Seeker' is neither a team (red, blue, green, yellow, aqua, light_purple, gold, white)"
                + " nor a role id in lower case letters, digits and dashes"), problems(invalid));
    }
}
