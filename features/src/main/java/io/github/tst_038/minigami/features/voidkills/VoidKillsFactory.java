package io.github.tst_038.minigami.features.voidkills;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.OptionalDouble;

/** {@code void-kills: { y: 0 }}: players below {@code y} die; without {@code y}, just below the lowest spawn. */
public final class VoidKillsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "void-kills";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new VoidKills(config.has("y") ? OptionalDouble.of(config.decimal("y")) : OptionalDouble.empty());
    }
}
