package io.github.tst_038.minigami.features.projectilebreaks;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Material;

/** {@code projectile-breaks: { blocks: [snow_block] }}. */
public final class ProjectileBreaksFactory implements FeatureFactory {

    private static final Setting<List<Material>> BLOCKS = Setting.of("blocks", Kind.materials(), "the blocks a projectile breaks on hitting");

    @Override
    public String id() {
        return "projectile-breaks";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(BLOCKS);
    }

    @Override
    public Feature create(ConfigReader config) {
        var blocks = new HashSet<>(config.read(BLOCKS));
        blocks.stream().filter(block -> !block.isBlock())
                .forEach(block -> config.problem("blocks", "'" + block.getKey().getKey() + "' is not a block"));
        return new ProjectileBreaks(blocks);
    }
}
