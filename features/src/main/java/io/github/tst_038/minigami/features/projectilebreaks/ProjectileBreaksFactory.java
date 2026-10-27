package io.github.tst_038.minigami.features.projectilebreaks;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;

/** {@code projectile-breaks: { blocks: [snow_block] }}. */
public final class ProjectileBreaksFactory implements FeatureFactory {

    @Override
    public String id() {
        return "projectile-breaks";
    }

    @Override
    public Feature create(ConfigReader config) {
        var blocks = new HashSet<>(config.materials("blocks"));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem("blocks", "'" + block.getKey().getKey() + "' is not a block"));
        return new ProjectileBreaks(blocks);
    }
}
