package io.github.tst_038.minigami.features.decayfloor;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;

/** {@code decay-floor: { delay-ticks: 8, blocks: [snow_block] }}; 20 ticks is a second. */
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
        return new DecayFloor(delay, blocks);
    }
}
