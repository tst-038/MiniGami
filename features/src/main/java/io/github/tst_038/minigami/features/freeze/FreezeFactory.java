package io.github.tst_038.minigami.features.freeze;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code freeze: {}}, without settings. */
public final class FreezeFactory implements FeatureFactory {

    @Override
    public String id() {
        return "freeze";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new Freeze();
    }
}
