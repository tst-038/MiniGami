package io.github.tst_038.minigami.features.nohunger;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code no-hunger: {}}, without settings. */
public final class NoHungerFactory implements FeatureFactory {

    @Override
    public String id() {
        return "no-hunger";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new NoHunger();
    }
}
