package io.github.tst_038.minigami.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class SettingsLoaderTest {

    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> 1;
                default -> throw new UnsupportedOperationException(method.getName());
            });

    private SettingsLoader.Loaded load(String yaml) {
        return SettingsLoader.load(yaml, name -> name.equals("world") ? world : null);
    }

    @Test
    void readsTheLobby() {
        var loaded = load("lobby: { world: world, x: 0.5, y: 64, z: 0.5, yaw: 180 }");

        assertEquals(List.of(), loaded.problems());
        assertEquals(Optional.of(new Location(world, 0.5, 64, 0.5, 180, 0)), loaded.settings().lobby());
    }

    @Test
    void theBundledFileHasNoLobby() {
        var loaded = load("# lobby: { world: world, x: 0.5, y: 64, z: 0.5 }");

        assertEquals(List.of(), loaded.problems());
        assertEquals(Settings.DEFAULTS, loaded.settings());
    }

    @Test
    void invalidFileFallsBackToDefaultsWithEveryProblem() {
        var loaded = load("lobby: { world: wrld, x: 1, y: 64 }\nlobbby: 1");

        assertEquals(Settings.DEFAULTS, loaded.settings());
        assertEquals(List.of(
                "config.yml: lobby.world: no loaded world 'wrld'",
                "config.yml: lobby.z: is missing",
                "config.yml: lobbby: unknown key"), loaded.problems().stream().map(ConfigProblem::toString).toList());
    }

    @Test
    void brokenYamlFallsBackToDefaults() {
        var loaded = load("lobby: [unclosed");

        assertEquals(Settings.DEFAULTS, loaded.settings());
        assertTrue(loaded.problems().getFirst().toString().startsWith("config.yml: can't be read"));
    }
}
