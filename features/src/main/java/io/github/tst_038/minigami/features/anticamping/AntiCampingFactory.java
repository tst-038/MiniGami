package io.github.tst_038.minigami.features.anticamping;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Material;

/** {@code anti-camping: { seconds: 3, blocks: [snow_block] }}. */
public final class AntiCampingFactory implements FeatureFactory {

    private static final Setting<Integer> SECONDS = Setting.of("seconds", Kind.integer(1, 3600), "seconds on one spot before the block under it goes");
    private static final Setting<List<Material>> BLOCKS = Setting.of("blocks", Kind.materials(), "the blocks that go when camped on");

    @Override
    public String id() {
        return "anti-camping";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(SECONDS, BLOCKS);
    }

    @Override
    public Feature create(ConfigReader config) {
        var seconds = config.read(SECONDS);
        var blocks = new HashSet<>(config.read(BLOCKS));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem(BLOCKS.key(), "'" + block.getKey().getKey() + "' is not a block"));
        return new AntiCamping(seconds * 20L, blocks);
    }
}
