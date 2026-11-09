package io.github.tst_038.minigami.features.decayfloor;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;

/**
 * {@code decay-floor: { delay-ticks: 8, blocks: [snow_block] }}; 20 ticks is a second. Optional
 * {@code break-below: [tnt]} also removes those blocks right under a broken one, and {@code fall: true}
 * lets removed blocks drop away instead of vanishing, as flashing primed TNT that never explodes with
 * {@code lit: true}. {@code respawn-grace-ticks: 20} leaves a player who respawns alone for that long.
 */
public final class DecayFloorFactory implements FeatureFactory {

    private static final Setting<Integer> DELAY = Setting.of("delay-ticks", Kind.integer(1, 200),
            "ticks a block lasts once stood on; 20 is a second");
    private static final Setting<List<Material>> BLOCKS = Setting.of("blocks", Kind.materials(), "the blocks that decay");
    private static final Setting<List<Material>> BREAK_BELOW = Setting.of("break-below", Kind.materials(),
            "blocks also removed right under a decayed one").orElse(List.of());
    private static final Setting<Boolean> FALL = Setting.of("fall", Kind.bool(), "removed blocks drop away instead of vanishing").orElse(false);
    private static final Setting<Boolean> LIT = Setting.of("lit", Kind.bool(), "falling blocks show as primed TNT that never explodes").orElse(false);
    private static final Setting<Integer> RESPAWN_GRACE = Setting.of("respawn-grace-ticks", Kind.integer(0, 1200),
            "ticks a respawned player's floor is left alone").orElse(0);

    @Override
    public String id() {
        return "decay-floor";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(DELAY, BLOCKS, BREAK_BELOW, FALL, LIT, RESPAWN_GRACE);
    }

    @Override
    public Feature create(ConfigReader config) {
        return new DecayFloor(config.read(DELAY), blocks(config, BLOCKS), blocks(config, BREAK_BELOW), config.read(FALL), config.read(LIT),
                config.read(RESPAWN_GRACE));
    }

    private static Set<Material> blocks(ConfigReader config, Setting<List<Material>> setting) {
        var blocks = new HashSet<>(config.read(setting));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem(setting.key(), "'" + block.getKey().getKey() + "' is not a block"));
        return blocks;
    }
}
