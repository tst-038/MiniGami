package io.github.tst_038.minigami.features.kit;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code kit: { items: [diamond_shovel] }}: one of each item. */
public final class KitFactory implements FeatureFactory {

    @Override
    public String id() {
        return "kit";
    }

    @Override
    public Feature create(ConfigReader config) {
        var items = config.materials("items");
        items.stream().filter(item -> !item.isItem())
                .forEach(item -> config.problem("items", "'" + item.getKey().getKey() + "' is not an item"));
        return new Kit(items);
    }
}
