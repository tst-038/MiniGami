package io.github.tst_038.minigami.features.voidkills;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** {@code void-kills: { y: 0 }}: players below {@code y} die; without {@code y}, just below the lowest spawn. */
public final class VoidKillsFactory implements FeatureFactory {

    private static final Setting<Optional<Double>> Y = Setting.of("y", Kind.optional(Kind.decimal()),
            "the level below which players die; just below the lowest spawn when left out").orElse(Optional.empty());

    @Override
    public String id() {
        return "void-kills";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(Y);
    }

    @Override
    public Feature create(ConfigReader config) {
        return new VoidKills(config.read(Y).map(OptionalDouble::of).orElse(OptionalDouble.empty()));
    }
}
