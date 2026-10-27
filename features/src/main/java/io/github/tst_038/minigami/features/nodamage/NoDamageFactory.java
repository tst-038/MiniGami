package io.github.tst_038.minigami.features.nodamage;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/** {@code no-damage: { causes: [fall, entity-attack] }}, with Bukkit's damage causes in lower case with dashes. */
public final class NoDamageFactory implements FeatureFactory {

    @Override
    public String id() {
        return "no-damage";
    }

    @Override
    public Feature create(ConfigReader config) {
        config.suggest("causes", Arrays.stream(DamageCause.values()).map(NoDamageFactory::configName).toList());
        var causes = new HashSet<DamageCause>();
        for (String name : config.strings("causes")) {
            Arrays.stream(DamageCause.values()).filter(cause -> configName(cause).equals(name)).findFirst()
                    .ifPresentOrElse(causes::add, () -> config.problem("causes", "unknown damage cause '" + name + "'"));
        }
        return new NoDamage(causes);
    }

    private static String configName(DamageCause cause) {
        return cause.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
