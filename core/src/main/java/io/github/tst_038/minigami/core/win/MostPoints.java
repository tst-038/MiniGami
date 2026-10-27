package io.github.tst_038.minigami.core.win;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Most points wins once the match runs its course, with equal points sharing a place and the places
 * after skipping ahead (1, 1, 3). A team's points are the sum of its members'; a player without a
 * team is a team of one. It is decided early only when at most one team is left.
 */
public final class MostPoints implements WinCondition {

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        return roster.teamsLeft().size() > 1 ? Optional.empty() : Optional.of(finalResult(roster, scores));
    }

    /** Teams still in rank above those out, whatever their points. */
    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        var placements = new ArrayList<Placement>();
        int placed = rank(roster.teamsLeft(), scores, placements, 0);
        rank(roster.teamsOut(), scores, placements, placed);
        return new MatchResult(placements);
    }

    /** @return how many teams are placed after these */
    private static int rank(List<MatchTeam> teams, Scores scores, List<Placement> placements, int placed) {
        var byPoints = new TreeMap<Integer, List<MatchTeam>>(Comparator.reverseOrder());
        for (MatchTeam team : teams) {
            int points = team.ids().stream().mapToInt(scores::of).sum();
            byPoints.computeIfAbsent(points, key -> new ArrayList<>()).add(team);
        }
        for (List<MatchTeam> tied : byPoints.values()) {
            placements.add(new Placement(placed + 1, tied.stream().flatMap(team -> team.ids().stream()).toList()));
            placed += tied.size();
        }
        return placed;
    }
}
