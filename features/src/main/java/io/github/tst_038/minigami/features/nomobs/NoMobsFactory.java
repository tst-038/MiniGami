package io.github.tst_038.minigami.features.nomobs;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code no-mobs: {}}, without settings. */
public final class NoMobsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "no-mobs";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new NoMobs();
    }
}
