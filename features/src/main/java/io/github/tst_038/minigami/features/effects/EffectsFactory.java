package io.github.tst_038.minigami.features.effects;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;

/** {@code effects: { blindness: 1, speed: 2 }}: potion effects by name, each with its level (1 to 255). Without particles. */
public final class EffectsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "effects";
    }

    @Override
    public Feature create(ConfigReader config) {
        Registry.MOB_EFFECT.forEach(type -> config.suggest(type.getKey().getKey(), List.of("1")));
        var effects = new ArrayList<PotionEffect>();
        for (String name : config.keys()) {
            var key = NamespacedKey.fromString(name);
            var type = key == null ? null : Registry.MOB_EFFECT.get(key);
            var level = config.integer(name, 1, 255);
            if (type == null) {
                config.problem(name, "unknown potion effect");
            } else {
                effects.add(new PotionEffect(type, PotionEffect.INFINITE_DURATION, level - 1, false, false));
            }
        }
        return new Effects(effects);
    }
}
