package io.github.tst_038.minigami.features.anticamping;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;

/** {@code anti-camping: { seconds: 3, blocks: [snow_block] }}. */
public final class AntiCampingFactory implements FeatureFactory {

    @Override
    public String id() {
        return "anti-camping";
    }

    @Override
    public Feature create(ConfigReader config) {
        var seconds = config.integer("seconds", 1, 3600);
        var blocks = new HashSet<>(config.materials("blocks"));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem("blocks", "'" + block.getKey().getKey() + "' is not a block"));
        return new AntiCamping(seconds * 20L, blocks);
    }
}
