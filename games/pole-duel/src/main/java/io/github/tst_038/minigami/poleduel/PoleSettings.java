package io.github.tst_038.minigami.poleduel;

import io.github.tst_038.minigami.api.config.ConfigReader;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * The top-level settings of a Pole Duel game file; every one is optional.
 *
 * @param height blocks of pole above the arena's floor, the bottom layer of the {@code poles} region
 * @param minDistance the least distance between two poles, in blocks
 * @param slabs the most slabs a player holds; resupplies stop there
 * @param resupplySeconds seconds between the slabs handed out
 * @param maxPlayers the most players a match can have, to check the arena has room for their poles
 */
record PoleSettings(int height, int minDistance, Material pole, Material slab, int slabs, int resupplySeconds, int maxPlayers) {

    static PoleSettings defaults() {
        return read(ConfigReader.of("", new MemoryConfiguration()));
    }

    static PoleSettings read(ConfigReader config) {
        return new PoleSettings(
                integer(config, "pole-height", 3, 60, 6),
                integer(config, "min-distance", 2, 30, 4),
                material(config, "pole-material", Material.OAK_LOG),
                material(config, "slab-material", Material.OAK_SLAB),
                integer(config, "slabs", 1, 64, 64),
                integer(config, "resupply-seconds", 1, 600, 5),
                integer(config, "max-players", 1, 1000, 16));
    }

    private static int integer(ConfigReader config, String key, int min, int max, int fallback) {
        return config.has(key) ? config.integer(key, min, max) : fallback;
    }

    private static Material material(ConfigReader config, String key, Material fallback) {
        if (!config.has(key)) {
            return fallback;
        }
        var material = Material.matchMaterial(config.string(key));
        // shortcut: only the name is checked, since Material.isBlock needs a running server; a non-block fails when the fill runs
        if (material == null) {
            config.problem(key, "must be a block");
            return fallback;
        }
        return material;
    }
}
