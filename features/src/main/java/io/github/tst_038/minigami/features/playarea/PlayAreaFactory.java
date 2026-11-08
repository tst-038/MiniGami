package io.github.tst_038.minigami.features.playarea;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.region.TeamRegion;
import java.util.Optional;

/** {@code play-area: { region: fighting }} for a region everybody shares ({@code map} is the box of the arena's saved map), or {@code region: team} for each team's own. */
public final class PlayAreaFactory implements FeatureFactory {

    private static final String MAP = "map";

    @Override
    public String id() {
        return "play-area";
    }

    @Override
    public Feature create(ConfigReader config) {
        var region = config.string("region");
        return new PlayArea(region.equals(TeamRegion.KEYWORD) ? Optional.empty() : Optional.of(region), false);
    }

    /** Everyone stays in the arena's saved map; does nothing for an arena without one. The default of every phase that has no play-area of its own. */
    public static Feature onMap() {
        return new PlayArea(Optional.of(MAP), true);
    }

    public static boolean isPlayArea(Feature feature) {
        return feature instanceof PlayArea;
    }
}
