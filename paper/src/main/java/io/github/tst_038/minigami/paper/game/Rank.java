package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.core.team.RoleShare;
import java.util.List;
import java.util.Map;

/**
 * A part within a team, such as king or guard: handed out at the start, at random, in every team on its own.
 * Unlike a role it does not split the team in two.
 *
 * @param share how many of each team get it
 * @param features per phase, on top of the game's; they only see the rank's holders
 */
public record Rank(String id, RoleShare share, Map<String, List<Feature>> features) {

    public Rank {
        features = Map.copyOf(features);
    }
}
