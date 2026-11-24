package io.github.tst_038.minigami.api.region;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RegionTest {

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private final World world = world();
    private final Region hill = Region.between("hill", world, 10, 64, -5, 0, 66, 5);

    @Test
    void cornersMayBeGivenInAnyOrder() {
        assertEquals(new Region("hill", world, 0, 64, -5, 10, 66, 5), hill);
    }

    @Test
    void containsTheBlocksFromCornerToCornerBothIncluded() {
        assertTrue(hill.contains(new Location(world, 0, 64, -5)));
        assertTrue(hill.contains(new Location(world, 10.9, 66.9, 5.9)));
        assertTrue(hill.contains(new Location(world, 5.5, 65, 0.5)));

        assertFalse(hill.contains(new Location(world, 11, 65, 0)));
        assertFalse(hill.contains(new Location(world, -0.1, 65, 0)));
        assertFalse(hill.contains(new Location(world, 5, 67, 0)));
        assertFalse(hill.contains(new Location(world, 5, 63.9, 0)));
    }

    @Test
    void onlyInItsOwnWorld() {
        assertFalse(hill.contains(new Location(world(), 5, 65, 0)));
    }

    @Test
    void theMinimumCornerMustComeFirst() {
        assertThrows(IllegalArgumentException.class, () -> new Region("hill", world, 1, 0, 0, 0, 0, 0));
    }

    @Test
    void aGroupHasItsOwnNameAndThoseStartingWithItAndADash() {
        assertTrue(new Region("wall", world, 0, 0, 0, 0, 0, 0).isIn("wall"));
        assertTrue(new Region("wall-north", world, 0, 0, 0, 0, 0, 0).isIn("wall"));
        assertFalse(new Region("walls", world, 0, 0, 0, 0, 0, 0).isIn("wall"));
        assertFalse(new Region("hill", world, 0, 0, 0, 0, 0, 0).isIn("wall"));
    }

    private ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("arenas/walls/arena.yml", config);
    }

    @Test
    void readsTheBoxBetweenTwoCorners() throws Exception {
        var root = root("""
                hill: { from: { x: 10, y: 66, z: -5 }, to: { x: 0, y: 64, z: 5 } }
                """);

        assertEquals(hill, Region.read("hill", root.section("hill"), world));
        assertDoesNotThrow(root::check);
    }

    @Test
    void reportsCornersOutsideTheWorld() throws Exception {
        var root = root("""
                hill: { from: { x: 0, y: 3000, z: 0 }, to: { x: 40000000, y: 64, z: 0 } }
                """);

        Region.read("hill", root.section("hill"), world);

        var problems = assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
        assertEquals(2, problems.size());
        assertTrue(problems.get(0).contains("hill.from.y"), problems.toString());
        assertTrue(problems.get(1).contains("hill.to.x"), problems.toString());
    }
}
