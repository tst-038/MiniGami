package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;

/**
 * {@code break-blocks: { blocks: [snow_block], instant: true, drops: false, place: true }}.
 * {@code blocks} lists the only breakable blocks, {@code except} lists the only unbreakable ones; give at most one,
 * and with neither every block is breakable. {@code instant} is off, {@code drops} on and {@code place} off unless set.
 * {@code place} lets players place any block; the lists never restrict placing.
 */
public final class BreakBlocksFactory implements FeatureFactory {

    @Override
    public String id() {
        return "break-blocks";
    }

    @Override
    public Feature create(ConfigReader config) {
        if (config.has("blocks") && config.has("except")) {
            config.problem("except", "can't be combined with 'blocks'; list the breakable blocks or the unbreakable ones");
        }
        var breakable = config.has("blocks") ? Breakable.only(blocks(config, "blocks"))
                : config.has("except") ? Breakable.except(blocks(config, "except")) : Breakable.any();
        var instant = config.has("instant") && config.bool("instant");
        var drops = !config.has("drops") || config.bool("drops");
        var place = config.has("place") && config.bool("place");
        return new BreakBlocks(breakable, instant, drops, place);
    }

    private static Set<Material> blocks(ConfigReader config, String key) {
        var blocks = new HashSet<>(config.materials(key));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem(key, "'" + block.getKey().getKey() + "' is not a block"));
        return blocks;
    }
}
