package io.github.tst_038.minigami.core.win;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The match is over when at most one participant is alive. The survivor places first and the
 * others follow in reverse elimination order. If nobody survives, the last one eliminated wins.
 */
public final class LastManStanding implements WinCondition {

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        return roster.alive().size() > 1 ? Optional.empty() : Optional.of(finalResult(roster, scores));
    }

    /** Everyone still alive shares first place; the others follow in reverse elimination order. */
    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        var placements = new ArrayList<Placement>();
        var alive = roster.alive().stream().map(Participant::id).toList();
        if (!alive.isEmpty()) {
            placements.add(new Placement(1, alive));
        }
        int place = alive.size() + 1;
        for (Participant participant : roster.eliminationOrder().reversed()) {
            placements.add(new Placement(place++, List.of(participant.id())));
        }
        return new MatchResult(placements);
    }
}
