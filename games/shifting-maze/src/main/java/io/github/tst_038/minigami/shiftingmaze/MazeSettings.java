package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * The top-level settings of a Shifting Maze game file; every one is optional.
 *
 * @param safeZoneRadius cells around the exit that in which a runner with all its keys cannot be caught; empty when the safe zone is off
 * @param warning how a coming wall shift is announced; empty when the cues are off
 * @param sight what the minotaurs are shown now and then; empty when it is off
 */
record MazeSettings(int cellWidth, int wallThickness, int wallHeight, int loops, int headStartSeconds, Optional<Integer> safeZoneRadius,
                    int runSeconds, int shiftSeconds, int shifts, Optional<Warning> warning, int keys, Optional<Sight> sight, Blocks blocks) {

    record Warning(int seconds, boolean flicker, boolean particles, String sound) {
    }

    record Sight(int everySeconds, int seconds) {
    }

    /**
     * @param floor weight per block, each block of the floor picking one at random
     * @param wall weight per block, as the floor
     * @param ceiling empty for an open top; {@code key} is an item, the others are blocks
     */
    record Blocks(Map<Material, Integer> floor, Map<Material, Integer> wall, Optional<Material> ceiling, Material warning, Material exit, Material key) {
    }

    private static final Setting<Integer> CELL_WIDTH = Setting.of("cell-width", Kind.integer(2, 8), "blocks across a maze cell").orElse(3);
    private static final Setting<Integer> WALL_THICKNESS = Setting.of("wall-thickness", Kind.integer(1, 3), "blocks thick a wall is").orElse(1);
    private static final Setting<Integer> WALL_HEIGHT = Setting.of("wall-height", Kind.integer(2, 8), "blocks high the walls are").orElse(3);
    private static final Setting<Integer> LOOPS = Setting.of("loops", Kind.integer(0, 100), "extra openings knocked through the walls").orElse(15);
    private static final Setting<Integer> HEAD_START_SECONDS = Setting.of("head-start-seconds", Kind.integer(0, 120),
            "how long the runners are ahead before the minotaurs are let loose").orElse(10);
    private static final Setting<Integer> RUN_SECONDS = Setting.of("run-seconds", Kind.integer(30, 3600), "how long the chase lasts").orElse(300);
    private static final Setting<Integer> SHIFT_SECONDS = Setting.of("shift-seconds", Kind.integer(5, 600), "seconds between wall shifts").orElse(20);
    private static final Setting<Integer> SHIFTS = Setting.of("shifts", Kind.integer(1, 50), "walls moved at each shift").orElse(3);
    private static final Setting<Integer> KEYS = Setting.of("keys", Kind.integer(0, 20), "keys a runner must find before the exit opens").orElse(3);

    private static final Setting<Integer> WARNING_SECONDS = Setting.of("seconds", Kind.integer(1, 30), "how long before a shift it is announced").orElse(3);
    private static final Setting<Boolean> FLICKER = Setting.of("flicker", Kind.bool(), "the walls about to move flicker").orElse(true);
    private static final Setting<Boolean> PARTICLES = Setting.of("particles", Kind.bool(), "the walls about to move show particles").orElse(true);
    private static final Setting<String> SOUND = Setting.of("sound", Kind.text(), "the sound played when a shift is coming").orElse("block.grindstone.use");
    private static final Setting<ConfigReader> WARNING = Setting.of("warning", Kind.section(List.of(WARNING_SECONDS, FLICKER, PARTICLES, SOUND)),
            "how a coming shift is announced; false turns the cues off");

    private static final Setting<Integer> RADIUS = Setting.of("radius", Kind.integer(0, 3), "cells around the exit where a runner with all its keys cannot be caught")
            .orElse(0);
    private static final Setting<ConfigReader> SAFE_ZONE = Setting.of("safe-zone", Kind.section(List.of(RADIUS)),
            "the safe zone around the exit; false turns it off");

    private static final Setting<Integer> EVERY_SECONDS = Setting.of("every-seconds", Kind.integer(5, 600), "seconds between the minotaurs' sight").orElse(30);
    private static final Setting<Integer> SIGHT_SECONDS = Setting.of("seconds", Kind.integer(1, 30), "how long the minotaurs see the runners").orElse(3);
    private static final Setting<ConfigReader> SIGHT = Setting.of("sight", Kind.section(List.of(EVERY_SECONDS, SIGHT_SECONDS)),
            "what the minotaurs are shown now and then; false turns it off");

    private static final Map<String, Integer> DUNGEON_FLOOR = Map.of("cobblestone", 35, "mossy_cobblestone", 20, "stone", 25, "gravel", 8, "andesite", 8,
            "water", 3, "shroomlight", 2);
    private static final Map<String, Integer> DUNGEON_WALL = Map.of("stone_bricks", 40, "mossy_stone_bricks", 20, "cracked_stone_bricks", 15,
            "cobblestone", 15, "mossy_cobblestone", 10);
    private static final Setting<Map<String, Integer>> FLOOR = Setting.of("floor", Kind.weighted("block", 1000),
            "a block, or blocks with their weights, each floor block picking one at random").orElse(DUNGEON_FLOOR);
    private static final Setting<Map<String, Integer>> WALL = Setting.of("wall", Kind.weighted("block", 1000), "the walls' blocks, as the floor's")
            .orElse(DUNGEON_WALL);
    // shortcut: only the names are checked, since Material.isBlock/isSolid need a running server; a non-block fails when the fill runs
    private static final Setting<Optional<Material>> CEILING = Setting.of("ceiling", Kind.optional(Kind.material()), "a block to roof the maze with; left out, it is open")
            .orElse(Optional.empty());
    private static final Setting<Material> WARNING_BLOCK = Setting.of("warning", Kind.material(), "block the walls about to move turn into")
            .orElse(Material.CRACKED_STONE_BRICKS);
    private static final Setting<Material> EXIT = Setting.of("exit", Kind.material(), "block marking the exit").orElse(Material.GOLD_BLOCK);
    private static final Setting<Material> KEY = Setting.of("key", Kind.material(), "the item a key is").orElse(Material.TRIPWIRE_HOOK);
    private static final Setting<ConfigReader> BLOCKS = Setting.of("blocks", Kind.section(List.of(FLOOR, WALL, CEILING, WARNING_BLOCK, EXIT, KEY)),
            "the blocks and items the maze is made of");

    static final List<Setting<?>> ALL = List.of(CELL_WIDTH, WALL_THICKNESS, WALL_HEIGHT, LOOPS, HEAD_START_SECONDS, SAFE_ZONE, RUN_SECONDS, SHIFT_SECONDS,
            SHIFTS, WARNING, KEYS, SIGHT, BLOCKS);

    static MazeSettings defaults() {
        return read(ConfigReader.of("", new MemoryConfiguration()));
    }

    static MazeSettings read(ConfigReader config) {
        int shiftSeconds = config.read(SHIFT_SECONDS);
        var warning = config.isOff(WARNING.key()) ? Optional.<Warning>empty() : Optional.of(readWarning(config.read(WARNING), shiftSeconds));
        var safeZone = config.isOff(SAFE_ZONE.key()) ? Optional.<Integer>empty() : Optional.of(config.read(SAFE_ZONE).read(RADIUS));
        var sight = config.isOff(SIGHT.key()) ? Optional.<Sight>empty() : Optional.of(readSight(config.read(SIGHT)));
        return new MazeSettings(
                config.read(CELL_WIDTH),
                config.read(WALL_THICKNESS),
                config.read(WALL_HEIGHT),
                config.read(LOOPS),
                config.read(HEAD_START_SECONDS),
                safeZone,
                config.read(RUN_SECONDS),
                shiftSeconds,
                config.read(SHIFTS),
                warning,
                config.read(KEYS),
                sight,
                readBlocks(config.read(BLOCKS)));
    }

    private static Warning readWarning(ConfigReader config, int shiftSeconds) {
        int seconds = config.read(WARNING_SECONDS);
        if (seconds >= shiftSeconds) {
            config.problem("seconds", "must be less than shift-seconds (" + shiftSeconds + ")");
        }
        return new Warning(seconds, config.read(FLICKER), config.read(PARTICLES), config.read(SOUND));
    }

    private static Sight readSight(ConfigReader config) {
        return new Sight(config.read(EVERY_SECONDS), config.read(SIGHT_SECONDS));
    }

    private static Blocks readBlocks(ConfigReader config) {
        return new Blocks(
                mix(config, FLOOR),
                mix(config, WALL),
                config.read(CEILING),
                config.read(WARNING_BLOCK),
                config.read(EXIT),
                config.read(KEY));
    }

    /** A block name, or a section of block names with their weights. */
    private static Map<Material, Integer> mix(ConfigReader config, Setting<Map<String, Integer>> setting) {
        var key = setting.key();
        var weights = config.read(setting);
        var mix = new LinkedHashMap<Material, Integer>();
        weights.forEach((name, weight) -> {
            var material = Material.matchMaterial(name);
            if (material == null) {
                config.problem(config.isSection(key) ? key + "." + name : key, "must be a block");
            } else {
                mix.merge(material, weight, Integer::sum);
            }
        });
        if (mix.isEmpty()) {
            if (config.has(key)) {
                config.problem(key, "needs at least one block");
            }
            return setting.fallback().orElseThrow().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(entry -> Material.matchMaterial(entry.getKey()), Map.Entry::getValue));
        }
        return mix;
    }
}
