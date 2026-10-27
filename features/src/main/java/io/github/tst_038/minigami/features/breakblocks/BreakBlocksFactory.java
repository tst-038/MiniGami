package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;

/**
 * {@code break-blocks: { blocks: [snow_block], instant: true, snowballs: true, camp-seconds: 3 }};
 * all but {@code blocks} are optional, and {@code camp-seconds: 0} turns camping off.
 */
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
        var snowballs = config.has("snowballs") && config.bool("snowballs");
        var campSeconds = config.has("camp-seconds") ? config.integer("camp-seconds", 0, 3600) : 0;
        return new BreakBlocks(blocks, instant, snowballs, campSeconds * 20L);
    }
}
