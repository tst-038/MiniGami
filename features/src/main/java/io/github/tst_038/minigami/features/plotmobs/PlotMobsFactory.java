package io.github.tst_038.minigami.features.plotmobs;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.List;

/** {@code plot-mobs: { max: 10 }}; at most 10 mobs per team region unless set. */
public final class PlotMobsFactory implements FeatureFactory {

    private static final Setting<Integer> MAX = Setting.of("max", Kind.integer(1, 1000), "most mobs a team region may hold").orElse(10);

    private static final Setting<Boolean> BREAK_BLOCKS = Setting.of("break-blocks", Kind.bool(), "whether mobs may break blocks inside their own plot; false keeps a finished build whole").orElse(true);

    @Override
    public String id() {
        return "plot-mobs";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(MAX, BREAK_BLOCKS);
    }

    @Override
    public Feature create(ConfigReader config) {
        return new PlotMobs(config.read(MAX), config.read(BREAK_BLOCKS));
    }
}
