package io.github.tst_038.minigami.features.playarea;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.region.TeamRegion;
import java.util.Optional;

/** {@code play-area: { region: fighting }} for a region everybody shares, or {@code region: team} for each team's own. */
public final class PlayAreaFactory implements FeatureFactory {

    @Override
    public String id() {
        return "play-area";
    }

    @Override
    public Feature create(ConfigReader config) {
        var region = config.string("region");
        return new PlayArea(region.equals(TeamRegion.KEYWORD) ? Optional.empty() : Optional.of(region));
    }
}
