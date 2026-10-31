package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;

/**
 * {@code break-blocks: { whitelist: [snow_block], blacklist: [bedrock], instant: true, drops: false, place: true }}.
 * A block may be broken if there is no {@code whitelist} or it is on it, and it is not on the {@code blacklist};
 * with neither list every block is breakable, and an empty {@code whitelist} breaks nothing.
 * {@code instant} is off, {@code drops} on and {@code place} off unless set.
 * {@code place} lets players place any block; the lists never restrict placing.
 */
public final class BreakBlocksFactory implements FeatureFactory {

    @Override
    public String id() {
        return "break-blocks";
    }

    @Override
    public Feature create(ConfigReader config) {
        var whitelist = config.has("whitelist") ? Optional.of(blocks(config, "whitelist")) : Optional.<Set<Material>>empty();
        var blacklist = config.has("blacklist") ? blocks(config, "blacklist") : Set.<Material>of();
        var breakable = new Breakable(whitelist, blacklist);
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
