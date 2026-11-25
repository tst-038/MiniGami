package io.github.tst_038.minigami.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigLocationsTest {

    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    private final Map<String, World> worlds = Map.of("world", world);

    private static ConfigReader read(String yaml) throws InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("config.yml", config);
    }

    @Test
    void readsAPositionWithItsDirection() throws Exception {
        var reader = read("{ x: 0.5, y: 64, z: -3, yaw: 90, pitch: 10 }");

        var location = ConfigLocations.read(reader, world);

        assertSame(world, location.getWorld());
        assertEquals(0.5, location.getX());
        assertEquals(64, location.getY());
        assertEquals(-3, location.getZ());
        assertEquals(90f, location.getYaw());
        assertEquals(10f, location.getPitch());
        reader.check();
    }

    @Test
    void writesWhatItReadsRoundedForTheFile() throws Exception {
        var config = new YamlConfiguration();
        config.set("spot", ConfigLocations.write(new org.bukkit.Location(world, 0.123456, 64, -3.5, 90.04f, 10)));

        var reader = read(config.saveToString()).section("spot");
        var location = ConfigLocations.read(reader, world);

        assertEquals(0.12, location.getX());
        assertEquals(-3.5, location.getZ());
        assertEquals(90f, location.getYaw());
        reader.check();
    }

    @Test
    void writesTheWorldWhenAskedTo() {
        var mapped = ConfigLocations.writeWithWorld(new org.bukkit.Location(worldNamed("lobby"), 1, 2, 3));

        assertEquals("lobby", mapped.get("world"));
        assertEquals(1.0, mapped.get("x"));
    }

    private static World worldNamed(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> method.getName().equals("getName") ? name : null);
    }

    @Test
    void yawAndPitchAreZeroWhenLeftOut() throws Exception {
        var location = ConfigLocations.read(read("{ x: 1, y: 2, z: 3 }"), world);

        assertEquals(0f, location.getYaw());
        assertEquals(0f, location.getPitch());
    }

    @Test
    void aMissingCoordinateIsAProblem() throws Exception {
        var reader = read("{ x: 1, z: 3 }");

        ConfigLocations.read(reader, world);

        var problems = assertThrows(InvalidConfigException.class, reader::check).problems();
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().toString().contains("y"), problems.getFirst().toString());
    }

    @Test
    void readsAWorldByName() throws Exception {
        var reader = read("world: world");

        assertSame(world, ConfigLocations.world(reader, "world", worlds::get));
        reader.check();
    }

    @Test
    void aWorldThatIsNotLoadedIsAProblem() throws Exception {
        var reader = read("world: nether");

        assertNull(ConfigLocations.world(reader, "world", worlds::get));

        var problems = assertThrows(InvalidConfigException.class, reader::check).problems();
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().toString().contains("no loaded world 'nether'"), problems.getFirst().toString());
    }

    @Test
    void aLocationCanNameItsOwnWorld() throws Exception {
        var reader = read("{ world: world, x: 1, y: 2, z: 3 }");

        var location = ConfigLocations.readWithWorld(reader, worlds::get);

        assertSame(world, location.getWorld());
        assertEquals(2, location.getY());
        reader.check();
    }
}
