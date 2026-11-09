package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;

/**
 * {@code break-blocks: { whitelist: [snow_block], blacklist: [bedrock], instant: true, drops: false, place: true }}.
 * A block may be broken if there is no {@code whitelist} or it is on it, and it is not on the {@code blacklist};
 * with neither list every block is breakable, and an empty {@code whitelist} breaks nothing.
 * {@code instant} is off, {@code drops} on and {@code place} off unless set.
 * {@code tools: [diamond_shovel]} lets only players holding one of them break blocks at all, and instantly.
 * {@code place} lets players place any block; the lists never restrict placing.
 */
public final class BreakBlocksFactory implements FeatureFactory {

    private static final Setting<List<Material>> WHITELIST = Setting.of("whitelist", Kind.materials(),
            "the only blocks that may be broken; every block when left out, none when empty").orElse(List.of());
    private static final Setting<List<Material>> BLACKLIST = Setting.of("blacklist", Kind.materials(),
            "blocks that may not be broken").orElse(List.of());
    private static final Setting<Boolean> INSTANT = Setting.of("instant", Kind.bool(), "blocks break at once when hit").orElse(false);
    private static final Setting<Boolean> DROPS = Setting.of("drops", Kind.bool(), "broken blocks drop items").orElse(true);
    private static final Setting<Boolean> PLACE = Setting.of("place", Kind.bool(), "players may place any block").orElse(false);
    private static final Setting<List<Material>> TOOLS = Setting.of("tools", Kind.materials(),
            "only a player holding one of these may break blocks, and then instantly; anyone may when left out").orElse(List.of());

    @Override
    public String id() {
        return "break-blocks";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(WHITELIST, BLACKLIST, INSTANT, DROPS, PLACE, TOOLS);
    }

    @Override
    public Feature create(ConfigReader config) {
        var whitelist = config.has("whitelist") ? Optional.of(blocks(config, WHITELIST)) : Optional.<Set<Material>>empty();
        var blacklist = config.has("blacklist") ? blocks(config, BLACKLIST) : Set.<Material>of();
        var breakable = new Breakable(whitelist, blacklist);
        var instant = config.read(INSTANT);
        var drops = config.read(DROPS);
        var place = config.read(PLACE);
        var tools = Set.copyOf(config.read(TOOLS));
        return new BreakBlocks(breakable, instant, drops, place, tools);
    }

    private static Set<Material> blocks(ConfigReader config, Setting<List<Material>> setting) {
        var blocks = new HashSet<>(config.read(setting));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem(setting.key(), "'" + block.getKey().getKey() + "' is not a block"));
        return blocks;
    }
}
