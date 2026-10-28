package io.github.tst_038.minigami.features.combatstats;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code combat-stats: {}}, without settings. */
public final class CombatStatsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "combat-stats";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new CombatStats();
    }
}
