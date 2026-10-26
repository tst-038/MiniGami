package io.github.tst_038.minigami.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The match is over when at most one participant is alive. The survivor places first and the
 * others follow in reverse elimination order. If nobody survives, the last one eliminated wins.
 */
public final class LastStanding implements WinCondition {

    @Override
    public Optional<MatchResult> check(Roster roster) {
        var alive = roster.alive();
        if (alive.size() > 1) {
            return Optional.empty();
        }
        var ranking = new ArrayList<>(alive);
        ranking.addAll(roster.eliminationOrder().reversed());

        var placements = new ArrayList<Placement>();
        for (int i = 0; i < ranking.size(); i++) {
            placements.add(new Placement(i + 1, List.of(ranking.get(i).id())));
        }
        return Optional.of(new MatchResult(placements));
    }
}
