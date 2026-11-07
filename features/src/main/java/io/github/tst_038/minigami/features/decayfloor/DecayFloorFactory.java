package io.github.tst_038.minigami.features.decayfloor;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;
import org.bukkit.Material;

/**
 * {@code decay-floor: { delay-ticks: 8, blocks: [snow_block] }}; 20 ticks is a second. Optional
 * {@code break-below: [tnt]} also removes those blocks right under a broken one, and {@code fall: true}
 * lets removed blocks drop away instead of vanishing, as flashing primed TNT that never explodes with
 * {@code lit: true}. {@code respawn-grace-ticks: 20} leaves a player who respawns alone for that long.
 */
public final class DecayFloorFactory implements FeatureFactory {

    @Override
    public String id() {
        return "decay-floor";
    }

    @Override
    public Feature create(ConfigReader config) {
        var delay = config.integer("delay-ticks", 1, 200);
        var blocks = new HashSet<>(config.materials("blocks"));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem("blocks", "'" + block.getKey().getKey() + "' is not a block"));
        var below = config.has("break-below") ? new HashSet<>(config.materials("break-below")) : new HashSet<Material>();
        below.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem("break-below", "'" + block.getKey().getKey() + "' is not a block"));
        return new DecayFloor(delay, blocks, below, config.has("fall") && config.bool("fall"), config.has("lit") && config.bool("lit"),
                config.has("respawn-grace-ticks") ? config.integer("respawn-grace-ticks", 0, 1200) : 0);
    }
}
