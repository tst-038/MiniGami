package io.github.tst_038.minigami.features.effects;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;

/** {@code effects: { blindness: 1, speed: 2 }}: potion effects by name, each with its level (1 to 255). Without particles. */
public final class EffectsFactory implements FeatureFactory {

    // Effect names are the keys themselves, so the setting sits under the empty key.
    private static final Setting<Map<String, Integer>> LEVELS = Setting.of("",
            Kind.each("effect", () -> Registry.MOB_EFFECT.stream().map(type -> type.getKey().getKey()).toList(), Kind.integer(1, 255)),
            "potion effects by name, each with its level");

    @Override
    public String id() {
        return "effects";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(LEVELS);
    }

    @Override
    public Feature create(ConfigReader config) {
        var effects = new ArrayList<PotionEffect>();
        for (var entry : config.read(LEVELS).entrySet()) {
            var name = entry.getKey();
            var key = NamespacedKey.fromString(name);
            var type = key == null ? null : Registry.MOB_EFFECT.get(key);
            var level = entry.getValue();
            if (type == null) {
                config.problem(name, "unknown potion effect");
            } else {
                effects.add(new PotionEffect(type, PotionEffect.INFINITE_DURATION, level - 1, false, false));
            }
        }
        return new Effects(effects);
    }
}
