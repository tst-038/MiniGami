package io.github.tst_038.minigami.paper.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.YamlFiles;
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
        var files = new YamlFiles("arenas", Map.of(id, config), List.of());
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
        assertEquals(List.of(new Location(world, 0.5, 64, 0.5, 90, 10), new Location(world, 5, 64, 5)), arena.spawns());
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
                "arenas/winter.yml: world: no loaded world 'wintr'",
                "arenas/winter.yml: spawns[0].z: is missing"), problems(loaded));
    }

    @Test
    void fileNameMustBeAValidId() throws Exception {
        var loaded = load("Winter", "world: world");

        assertEquals(List.of("arenas/Winter.yml: Invalid id 'Winter': use lower case letters, digits and dashes"), problems(loaded));
    }

    @Test
    void handedOutLocationsCanBeChangedSafely() throws Exception {
        var arena = load("winter", "world: world\nspawns: [{ x: 0, y: 64, z: 0 }]").entries().find("winter").orElseThrow();

        arena.spawns().getFirst().add(0, 100, 0);

        assertEquals(64, arena.spawns().getFirst().getY());
    }
}
