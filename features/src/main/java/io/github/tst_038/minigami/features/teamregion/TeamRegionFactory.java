package io.github.tst_038.minigami.features.teamregion;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code team-region: {}}, without settings. */
public final class TeamRegionFactory implements FeatureFactory {

    @Override
    public String id() {
        return "team-region";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new Confine();
    }
}
