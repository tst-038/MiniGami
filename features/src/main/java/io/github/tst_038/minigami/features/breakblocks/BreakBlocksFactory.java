package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;

/** {@code break-blocks: { blocks: [snow_block], instant: true, drops: false }}; {@code instant} is off and {@code drops} on unless set. */
public final class BreakBlocksFactory implements FeatureFactory {

    @Override
    public String id() {
        return "break-blocks";
    }

    @Override
    public Feature create(ConfigReader config) {
        var blocks = new HashSet<>(config.materials("blocks"));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem("blocks", "'" + block.getKey().getKey() + "' is not a block"));
        var instant = config.has("instant") && config.bool("instant");
        var drops = !config.has("drops") || config.bool("drops");
        return new BreakBlocks(blocks, instant, drops);
    }
}
