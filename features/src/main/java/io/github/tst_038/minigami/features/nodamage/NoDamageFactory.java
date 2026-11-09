package io.github.tst_038.minigami.features.nodamage;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/** {@code no-damage: { causes: [fall, entity-attack] }}, with Bukkit's damage causes in lower case with dashes; {@code [all]} is every cause but the void and /kill. */
public final class NoDamageFactory implements FeatureFactory {

    /** The void and /kill always kill; void-kills relies on it too. */
    private static final Set<DamageCause> ALWAYS = Set.of(DamageCause.VOID, DamageCause.KILL);

    private static final Setting<List<String>> CAUSES = Setting.of("causes",
            Kind.strings(Stream.concat(Stream.of("all"), Arrays.stream(DamageCause.values()).filter(cause -> !ALWAYS.contains(cause)).map(NoDamageFactory::configName)).toList()),
            "damage causes that do nothing; all is every cause but the void and /kill");

    @Override
    public String id() {
        return "no-damage";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(CAUSES);
    }

    @Override
    public Feature create(ConfigReader config) {
        var causes = new HashSet<DamageCause>();
        for (String name : config.read(CAUSES)) {
            if (name.equals("all")) {
                Arrays.stream(DamageCause.values()).filter(cause -> !ALWAYS.contains(cause)).forEach(causes::add);
                continue;
            }
            Arrays.stream(DamageCause.values()).filter(cause -> configName(cause).equals(name)).findFirst()
                    .ifPresentOrElse(cause -> {
                        if (ALWAYS.contains(cause)) {
                            config.problem(CAUSES.key(), "'" + name + "' can't be turned off, or a player could fall forever");
                        } else {
                            causes.add(cause);
                        }
                    }, () -> config.problem("causes", "unknown damage cause '" + name + "'"));
        }
        return new NoDamage(causes);
    }

    private static String configName(DamageCause cause) {
        return cause.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
