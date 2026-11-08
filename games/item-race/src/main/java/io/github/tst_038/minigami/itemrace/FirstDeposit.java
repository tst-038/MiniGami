package io.github.tst_038.minigami.itemrace;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The first team to hold the needed points wins; a hand-in of a new item gives one (one point is enough when any item will do). The match is also over when at most one team
 * is left in, and that team wins. The other teams still in share the next place, those out follow in
 * reverse order of going out. When time is up and nobody deposited, there is no winner and no placement.
 */
final class FirstDeposit implements WinCondition {

    private final int needed;

    FirstDeposit(int needed) {
        this.needed = needed;
    }

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        return winner(roster, scores).isPresent() || roster.teamsLeft().size() <= 1 ? Optional.of(finalResult(roster, scores)) : Optional.empty();
    }

    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        var winner = winner(roster, scores);
        if (winner.isEmpty()) {
            return new MatchResult(List.of());
        }
        var placements = new ArrayList<Placement>();
        placements.add(new Placement(1, winner.get().ids()));
        var others = roster.teamsLeft().stream().filter(team -> !team.ids().equals(winner.get().ids())).toList();
        int place = 2;
        if (!others.isEmpty()) {
            placements.add(new Placement(place++, others.stream().flatMap(team -> team.ids().stream()).toList()));
        }
        for (MatchTeam team : roster.teamsOut().reversed()) {
            placements.add(new Placement(place++, team.ids()));
        }
        return new MatchResult(placements);
    }

    /** The team that deposited, else the only one left in. */
    private Optional<MatchTeam> winner(Roster roster, Scores scores) {
        var left = roster.teamsLeft();
        var deposited = left.stream().filter(team -> team.ids().stream().mapToInt(scores::of).sum() >= needed).findFirst();
        return deposited.isPresent() || left.size() != 1 ? deposited : Optional.of(left.getFirst());
    }
}
