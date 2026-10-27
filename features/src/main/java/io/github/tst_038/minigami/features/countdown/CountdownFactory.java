package io.github.tst_038.minigami.features.countdown;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code countdown: { seconds: 10 }}; seconds is optional and 10 by default. */
public final class CountdownFactory implements FeatureFactory {

    @Override
    public String id() {
        return "countdown";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new Countdown(config.has("seconds") ? config.integer("seconds", 1, 60) : 10);
    }
}
