package io.github.tst_038.minigami.paper.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ArenaFileTest {

    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "world";
                case "equals" -> proxy == args[0];
                case "hashCode" -> 1;
                default -> throw new UnsupportedOperationException(method.getName());
            });

    /** Saves and loads the file the way the plugin does, so the edits are checked against the real loader. */
    private Arena reload(YamlConfiguration config) throws Exception {
        var saved = new YamlConfiguration();
        saved.loadFromString(config.saveToString());
        var loaded = ArenaLoader.load(new YamlFiles("arenas", Map.of("winter", saved), List.of()), name -> world);
        assertEquals(List.of(), loaded.problems());
        return loaded.entries().find("winter").orElseThrow();
    }

    @Test
    void editsLoadBackAsTheArena() throws Exception {
        var config = new YamlConfiguration();
        ArenaFile.create(config, world);
        ArenaFile.addSpawn(config, new Location(world, 0.5, 64, 0.5, 90, 10), Optional.empty());
        ArenaFile.addSpawn(config, new Location(world, 1.23456, 65, 2, -45.06f, 0), Optional.empty());
        ArenaFile.addSpawn(config, new Location(world, 9, 9, 9), Optional.of("red"));
        ArenaFile.removeSpawn(config, 0);

        var arena = reload(config);

        assertEquals(List.of(new Arena.Spawn(new Location(world, 1.23, 65, 2, -45.1f, 0), Optional.empty()),
                new Arena.Spawn(new Location(world, 9, 9, 9), Optional.of("red"))), arena.spawns());
    }

    @Test
    void regionsAreAddedReplacedInPlaceAndRemoved() throws Exception {
        var config = new YamlConfiguration();
        ArenaFile.create(config, world);
        ArenaFile.setRegion(config, new Region("hill", world, 0, 64, 0, 4, 66, 4));
        ArenaFile.setRegion(config, new Region("base", world, 9, 60, 9, 9, 60, 9));
        ArenaFile.setRegion(config, new Region("hill", world, -2, 64, -2, 2, 70, 2));

        assertEquals(List.of(new Region("hill", world, -2, 64, -2, 2, 70, 2), new Region("base", world, 9, 60, 9, 9, 60, 9)),
                List.copyOf(reload(config).regions().values()));

        assertFalse(ArenaFile.removeRegion(config, "top"));
        assertTrue(ArenaFile.removeRegion(config, "hill"));
        assertTrue(ArenaFile.removeRegion(config, "base"));
        assertEquals(Map.of(), reload(config).regions());
        assertFalse(config.contains("regions"));
    }

    @Test
    void newArenaHasNoSpawns() throws Exception {
        var config = new YamlConfiguration();
        ArenaFile.create(config, world);

        assertFalse(reload(config).isPlayable());
    }

    @Test
    void removingAMissingSpawnChangesNothing() {
        var config = new YamlConfiguration();
        ArenaFile.create(config, world);
        ArenaFile.addSpawn(config, new Location(world, 0, 64, 0), Optional.empty());

        assertFalse(ArenaFile.removeSpawn(config, 1));
        assertEquals(1, config.getMapList("spawns").size());
    }
}
