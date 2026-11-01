package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * The top-level settings of a Shifting Maze game file; every one is optional.
 *
 * @param safeZoneRadius cells around the exit that minotaurs may not enter; empty when the safe zone is off
 * @param warning how a coming wall shift is announced; empty when the cues are off
 * @param sight what the minotaurs are shown now and then; empty when it is off
 */
record MazeSettings(int cellWidth, int wallThickness, int wallHeight, int loops, int headStartSeconds, Optional<Integer> safeZoneRadius,
                    int runSeconds, int shiftSeconds, int shifts, Optional<Warning> warning, int keys, Optional<Sight> sight, Blocks blocks) {

    record Warning(int seconds, boolean flicker, boolean particles, String sound) {
    }

    record Sight(int everySeconds, int seconds) {
    }

    /** @param ceiling empty for an open top; {@code key} is an item, the others are blocks */
    record Blocks(Material floor, Material wall, Optional<Material> ceiling, Material warning, Material exit, Material key, Material sight) {
    }

    static MazeSettings defaults() {
        return read(ConfigReader.of("", new MemoryConfiguration()));
    }

    static MazeSettings read(ConfigReader config) {
        int shiftSeconds = integer(config, "shift-seconds", 5, 600, 20);
        var warning = config.isOff("warning") ? Optional.<Warning>empty() : Optional.of(readWarning(section(config, "warning"), shiftSeconds));
        var safeZone = config.isOff("safe-zone") ? Optional.<Integer>empty() : Optional.of(integer(section(config, "safe-zone"), "radius", 0, 3, 0));
        var sight = config.isOff("sight") ? Optional.<Sight>empty() : Optional.of(readSight(section(config, "sight")));
        return new MazeSettings(
                integer(config, "cell-width", 2, 8, 3),
                integer(config, "wall-thickness", 1, 3, 1),
                integer(config, "wall-height", 2, 8, 3),
                integer(config, "loops", 0, 100, 15),
                integer(config, "head-start-seconds", 0, 120, 10),
                safeZone,
                integer(config, "run-seconds", 30, 3600, 300),
                shiftSeconds,
                integer(config, "shifts", 1, 50, 3),
                warning,
                integer(config, "keys", 0, 20, 3),
                sight,
                readBlocks(section(config, "blocks")));
    }

    private static Warning readWarning(ConfigReader config, int shiftSeconds) {
        int seconds = integer(config, "seconds", 1, 30, 3);
        if (seconds >= shiftSeconds) {
            config.problem("seconds", "must be less than shift-seconds (" + shiftSeconds + ")");
        }
        return new Warning(seconds, !config.has("flicker") || config.bool("flicker"), !config.has("particles") || config.bool("particles"),
                config.has("sound") ? config.string("sound") : "block.grindstone.use");
    }

    private static Sight readSight(ConfigReader config) {
        return new Sight(integer(config, "every-seconds", 5, 600, 30), integer(config, "seconds", 1, 30, 3));
    }

    private static Blocks readBlocks(ConfigReader config) {
        return new Blocks(
                block(config, "floor", Material.SMOOTH_STONE),
                block(config, "wall", Material.STONE_BRICKS),
                config.has("ceiling") ? Optional.of(block(config, "ceiling", Material.STONE_BRICKS)) : Optional.empty(),
                block(config, "warning", Material.CRACKED_STONE_BRICKS),
                block(config, "exit", Material.GOLD_BLOCK),
                config.has("key") ? item(config, "key") : Material.TRIPWIRE_HOOK,
                block(config, "sight", Material.WHITE_STAINED_GLASS));
    }

    private static int integer(ConfigReader config, String key, int min, int max, int fallback) {
        return config.has(key) ? config.integer(key, min, max) : fallback;
    }

    /** The section, or an empty one when the file has none, so every key falls back to its default. */
    private static ConfigReader section(ConfigReader config, String key) {
        return config.has(key) ? config.section(key) : ConfigReader.of("", new MemoryConfiguration());
    }

    private static Material block(ConfigReader config, String key, Material fallback) {
        if (!config.has(key)) {
            return fallback;
        }
        var material = Material.matchMaterial(config.string(key));
        // shortcut: only the name is checked, since Material.isBlock/isSolid need a running server; a non-block fails when the fill runs
        if (material == null) {
            config.problem(key, "must be a block");
            return fallback;
        }
        return material;
    }

    private static Material item(ConfigReader config, String key) {
        var material = Material.matchMaterial(config.string(key));
        if (material == null) {
            config.problem(key, "must be an item");
            return Material.TRIPWIRE_HOOK;
        }
        return material;
    }
}
