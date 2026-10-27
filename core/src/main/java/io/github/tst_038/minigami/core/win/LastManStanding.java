package io.github.tst_038.minigami.core.win;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.ArrayList;
import java.util.Optional;

/**
 * The match is over when at most one team has someone alive; a player without a team is a team of
 * one. That team places first and the others follow in reverse order of going out. If no team is
 * left, the last one out wins.
 */
public final class LastManStanding implements WinCondition {

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        return roster.teamsLeft().size() > 1 ? Optional.empty() : Optional.of(finalResult(roster, scores));
    }

    /** Every team still in shares first place; the others follow in reverse order of going out. */
    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        var placements = new ArrayList<Placement>();
        var left = roster.teamsLeft();
        if (!left.isEmpty()) {
            placements.add(new Placement(1, left.stream().flatMap(team -> team.ids().stream()).toList()));
        }
        int place = left.size() + 1;
        for (MatchTeam team : roster.teamsOut().reversed()) {
            placements.add(new Placement(place++, team.ids()));
        }
        return new MatchResult(placements);
    }
}
