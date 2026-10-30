package io.github.tst_038.minigami.features.plotmobs;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code plot-mobs: { max: 10, group: plot }}; at most 10 mobs per region of the {@code plot} group unless set. */
public final class PlotMobsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "plot-mobs";
    }

    @Override
    public Feature create(ConfigReader config) {
        var max = config.has("max") ? config.integer("max", 1, 1000) : 10;
        var group = config.has("group") ? config.string("group") : "plot";
        return new PlotMobs(group, max);
    }
}
