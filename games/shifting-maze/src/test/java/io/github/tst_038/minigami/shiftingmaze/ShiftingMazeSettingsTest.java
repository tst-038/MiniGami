package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ShiftingMazeSettingsTest {

    private static final Set<String> KEYS = Set.of("cell-width", "wall-thickness", "wall-height", "loops", "head-start-seconds", "run-seconds",
            "shift-seconds", "shifts", "keys", "warning", "warning.seconds", "warning.flicker", "warning.particles", "warning.sound", "safe-zone",
            "safe-zone.radius", "sight", "sight.every-seconds", "sight.seconds", "blocks", "blocks.floor", "blocks.floor.<block>", "blocks.wall",
            "blocks.wall.<block>", "blocks.ceiling", "blocks.warning", "blocks.exit", "blocks.key");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new ShiftingMaze().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                cell-width: 4
                wall-thickness: 2
                wall-height: 4
                loops: 5
                head-start-seconds: 5
                run-seconds: 120
                shift-seconds: 30
                shifts: 2
                keys: 1
                warning: { seconds: 4, flicker: false, particles: false, sound: block.anvil.land }
                safe-zone: { radius: 2 }
                sight: { every-seconds: 20, seconds: 2 }
                blocks:
                  floor: { stone: 3, gravel: 1 }
                  wall: stone_bricks
                  ceiling: stone
                  warning: cracked_stone_bricks
                  exit: gold_block
                  key: tripwire_hook
                """);
    }

    @Test
    void readsAMinimalConfigStrictly() throws Exception {
        configure("{}");
        configure("warning: false\nsafe-zone: false\nsight: false");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("shifting-maze.yml", yaml, new ShiftingMaze().settings());
        new ShiftingMaze().configure(config);
        config.check();
    }
}
