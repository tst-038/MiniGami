package io.github.tst_038.minigami.spleef;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;

/**
 * Classic spleef on a snow floor: shovel and snowballs, no camping, no hits or fall damage, and
 * whoever falls through the floor is out. The last one still on it wins.
 */
public final class Spleef implements GameType {

    @Override
    public String id() {
        return "spleef";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.untimed("playing"), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of("ending", List.of(new AnnounceWinner()));
    }

    @Override
    public Map<String, ?> defaultFeatures() {
        return Map.of("playing", Map.of(
                "kit", Map.of("items", List.of("diamond_shovel")),
                "break-blocks", Map.of("blocks", List.of("snow_block"), "instant", true, "snowballs", true, "camp-seconds", 3),
                "no-damage", Map.of("causes", List.of("fall", "entity-attack", "entity-sweep-attack")),
                "void-kills", Map.of()));
    }

    @Override
    public WinCondition winCondition() {
        return new LastManStanding();
    }

    @Override
    public int lives() {
        return 1;
    }
}
