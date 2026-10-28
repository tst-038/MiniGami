package io.github.tst_038.minigami.features.onehit;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code one-hit: {}}, without settings. */
public final class OneHitFactory implements FeatureFactory {

    @Override
    public String id() {
        return "one-hit";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new OneHit();
    }
}
