package io.github.tst_038.minigami.features.nodropitems;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code no-drop-items: {}}, without settings. */
public final class NoDropItemsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "no-drop-items";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new NoDropItems();
    }
}
