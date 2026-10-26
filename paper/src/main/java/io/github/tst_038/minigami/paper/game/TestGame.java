package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Last one standing wins; how players drop out comes from the features of the mode.
 *
 * <p>shortcut: a bare game to exercise the engine; becomes a proper example game with a bundled mode later.
 */
public final class TestGame implements GameType {

    @Override
    public String id() {
        return "test";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.untimed("playing"), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of("ending", List.of(announceWinner()));
    }

    @Override
    public WinCondition winCondition() {
        return new LastManStanding();
    }

    private static Feature announceWinner() {
        return context -> {
            var winner = context.result()
                    .flatMap(result -> result.placements().stream().filter(placement -> placement.place() == 1).findFirst())
                    .map(Placement::participants)
                    .flatMap(ids -> ids.stream().findFirst())
                    .flatMap(context::player);
            if (winner.isPresent()) {
                context.messages().send(context.audience(), "match.winner", Placeholder.component("player", winner.get().name()));
            } else {
                context.messages().send(context.audience(), "match.no-winner");
            }
        };
    }
}
