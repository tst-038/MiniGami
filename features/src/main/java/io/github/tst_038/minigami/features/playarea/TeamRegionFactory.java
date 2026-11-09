package io.github.tst_038.minigami.features.playarea;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.List;
import java.util.Optional;

/** {@code team-region: {}}, the name 0.2.0 gave to {@code play-area: { region: team }}. */
public final class TeamRegionFactory implements FeatureFactory {

    @Override
    public String id() {
        return "team-region";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of();
    }

    @Override
    public Feature create(ConfigReader config) {
        return new PlayArea(Optional.empty(), false);
    }
}
