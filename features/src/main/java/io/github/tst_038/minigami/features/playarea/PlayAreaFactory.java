package io.github.tst_038.minigami.features.playarea;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;

/** {@code play-area: { region: border }}: the arena region everyone stays in. */
public final class PlayAreaFactory implements FeatureFactory {

    @Override
    public String id() {
        return "play-area";
    }

    @Override
    public Feature create(ConfigReader config) {
        return new PlayArea(config.string("region"));
    }
}
