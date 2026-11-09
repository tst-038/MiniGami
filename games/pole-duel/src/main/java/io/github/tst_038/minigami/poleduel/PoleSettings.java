package io.github.tst_038.minigami.poleduel;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
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

    private static final Setting<Integer> HEIGHT = Setting.of("pole-height", Kind.integer(3, 60), "blocks of pole above the floor").orElse(6);
    private static final Setting<Integer> MIN_DISTANCE = Setting.of("min-distance", Kind.integer(2, 30), "least distance between two poles").orElse(4);
    // shortcut: only the name is checked, since Material.isBlock needs a running server; a non-block fails when the fill runs
    private static final Setting<Material> POLE = Setting.of("pole-material", Kind.material(), "block the poles are built of").orElse(Material.OAK_LOG);
    private static final Setting<Material> SLAB = Setting.of("slab-material", Kind.material(), "block handed out to bridge with").orElse(Material.OAK_SLAB);
    private static final Setting<Integer> SLABS = Setting.of("slabs", Kind.integer(1, 64), "most slabs a player holds; resupplies stop there").orElse(64);
    private static final Setting<Integer> RESUPPLY_SECONDS = Setting.of("resupply-seconds", Kind.integer(1, 600), "seconds between the slabs handed out").orElse(5);
    private static final Setting<Integer> MAX_PLAYERS = Setting.of("max-players", Kind.integer(1, 1000),
            "most players a match can have, to check the arena has room for their poles").orElse(16);

    static final List<Setting<?>> ALL = List.of(HEIGHT, MIN_DISTANCE, POLE, SLAB, SLABS, RESUPPLY_SECONDS, MAX_PLAYERS);

    static PoleSettings defaults() {
        return read(ConfigReader.of("", new MemoryConfiguration()));
    }

    static PoleSettings read(ConfigReader config) {
        return new PoleSettings(
                config.read(HEIGHT),
                config.read(MIN_DISTANCE),
                config.read(POLE),
                config.read(SLAB),
                config.read(SLABS),
                config.read(RESUPPLY_SECONDS),
                config.read(MAX_PLAYERS));
    }
}
