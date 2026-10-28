package io.github.tst_038.minigami.features.deathitems;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.features.deathitems.DeathItems.Outcome;

/** {@code death-items: { items: keep, xp: vanish }}, each {@code drop} (the default), {@code keep} or {@code vanish}. */
public final class DeathItemsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "death-items";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new DeathItems(outcome(config, "items"), outcome(config, "xp"));
    }

    private static Outcome outcome(ConfigReader config, String key) {
        return config.has(key) ? config.choice(key, Outcome.class) : Outcome.DROP;
    }
}
