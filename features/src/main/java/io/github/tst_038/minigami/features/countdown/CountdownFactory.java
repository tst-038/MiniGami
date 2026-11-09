package io.github.tst_038.minigami.features.countdown;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.List;

/** {@code countdown: { seconds: 10 }}; seconds is optional and 10 by default. */
public final class CountdownFactory implements FeatureFactory {

    private static final Setting<Integer> SECONDS = Setting.of("seconds", Kind.integer(1, 60), "seconds counted down before the phase starts").orElse(10);

    @Override
    public String id() {
        return "countdown";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(SECONDS);
    }

    @Override
    public Feature create(ConfigReader config) {
        return new Countdown(config.read(SECONDS));
    }
}
