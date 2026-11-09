package io.github.tst_038.minigami.features.deathitems;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.features.deathitems.DeathItems.Outcome;
import java.util.List;

/** {@code death-items: { items: keep, xp: vanish }}, each {@code drop} (the default), {@code keep} or {@code vanish}. */
public final class DeathItemsFactory implements FeatureFactory {

    private static final Setting<Outcome> ITEMS = Setting.of("items", Kind.choice(Outcome.class),
            "what happens to a dead player's items: drop, keep or vanish").orElse(Outcome.DROP);
    private static final Setting<Outcome> XP = Setting.of("xp", Kind.choice(Outcome.class),
            "what happens to a dead player's experience: drop, keep or vanish").orElse(Outcome.DROP);

    @Override
    public String id() {
        return "death-items";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(ITEMS, XP);
    }

    @Override
    public Feature create(ConfigReader config) {
        return new DeathItems(config.read(ITEMS), config.read(XP));
    }
}
