package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MazeSettingsTest {

    private static MazeSettings read(String yaml) throws InvalidConfigException, InvalidConfigurationException {
        var config = ConfigReader.of("maze.yml", load(yaml));
        var settings = MazeSettings.read(config);
        config.check();
        return settings;
    }

    private static YamlConfiguration load(String yaml) throws InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return configuration;
    }

    private static String problem(String yaml) throws InvalidConfigurationException {
        var config = ConfigReader.of("maze.yml", load(yaml));
        MazeSettings.read(config);
        return assertThrows(InvalidConfigException.class, config::check).getMessage();
    }

    @Test
    void anEmptyFileGivesTheDefaults() throws Exception {
        var settings = read("");

        assertEquals(settings, MazeSettings.defaults());
        assertEquals(3, settings.cellWidth());
        assertEquals(1, settings.wallThickness());
        assertEquals(3, settings.wallHeight());
        assertEquals(15, settings.loops());
        assertEquals(10, settings.headStartSeconds());
        assertEquals(Optional.of(0), settings.safeZoneRadius());
        assertEquals(300, settings.runSeconds());
        assertEquals(20, settings.shiftSeconds());
        assertEquals(3, settings.shifts());
        assertEquals(new MazeSettings.Warning(3, true, true, "block.grindstone.use"), settings.warning().orElseThrow());
        assertEquals(3, settings.keys());
        assertEquals(new MazeSettings.Sight(30, 3), settings.sight().orElseThrow());
        assertEquals(Material.STONE_BRICKS, settings.blocks().wall());
        assertEquals(Material.TRIPWIRE_HOOK, settings.blocks().key());
        assertEquals(Optional.empty(), settings.blocks().ceiling());
    }

    @Test
    void valuesAreTaken() throws Exception {
        var settings = read("cell-width: 4\nwall-height: 5\nkeys: 0\nshift-seconds: 40\nwarning: { seconds: 5, flicker: false }\n"
                + "blocks: { wall: deepslate_bricks, ceiling: stone }");

        assertEquals(4, settings.cellWidth());
        assertEquals(5, settings.wallHeight());
        assertEquals(0, settings.keys());
        assertEquals(new MazeSettings.Warning(5, false, true, "block.grindstone.use"), settings.warning().orElseThrow());
        assertEquals(Material.DEEPSLATE_BRICKS, settings.blocks().wall());
        assertEquals(Optional.of(Material.STONE), settings.blocks().ceiling());
    }

    @Test
    void valuesOutOfRangeAreReported() throws Exception {
        assertTrue(problem("cell-width: 1").contains("cell-width"));
        assertTrue(problem("cell-width: 9").contains("cell-width"));
        assertTrue(problem("wall-thickness: 4").contains("wall-thickness"));
        assertTrue(problem("wall-height: 1").contains("wall-height"));
        assertTrue(problem("loops: 101").contains("loops"));
        assertTrue(problem("head-start-seconds: 121").contains("head-start-seconds"));
        assertTrue(problem("run-seconds: 29").contains("run-seconds"));
        assertTrue(problem("shifts: 0").contains("shifts"));
        assertTrue(problem("keys: 21").contains("keys"));
        assertTrue(problem("safe-zone: { radius: 4 }").contains("radius"));
        assertTrue(problem("sight: { every-seconds: 4 }").contains("every-seconds"));
    }

    @Test
    void anUnknownBlockIsReported() throws Exception {
        assertTrue(problem("blocks: { wall: not_a_block }").contains("blocks.wall"));
        assertTrue(problem("blocks: { key: not_an_item }").contains("blocks.key"));
    }

    @Test
    void theWarningMustEndBeforeTheNextShift() throws Exception {
        assertTrue(problem("shift-seconds: 5\nwarning: { seconds: 5 }").contains("warning.seconds"));
        assertTrue(problem("shift-seconds: 6\nwarning: { seconds: 9 }").contains("warning.seconds"));
        assertEquals(4, read("shift-seconds: 5\nwarning: { seconds: 4 }").warning().orElseThrow().seconds());
    }

    @Test
    void falseTurnsTheOptionalPartsOff() throws Exception {
        var settings = read("warning: false\nsafe-zone: false\nsight: false");

        assertEquals(Optional.empty(), settings.warning());
        assertEquals(Optional.empty(), settings.safeZoneRadius());
        assertEquals(Optional.empty(), settings.sight());
        assertFalse(MazeSettings.defaults().warning().isEmpty());
    }

    @Test
    void anUnknownKeyIsReported() throws Exception {
        assertTrue(problem("cell-widht: 3").contains("cell-widht"));
        assertTrue(problem("blocks: { walls: stone }").contains("blocks.walls"));
    }
}
