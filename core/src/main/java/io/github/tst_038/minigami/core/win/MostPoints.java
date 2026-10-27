package io.github.tst_038.minigami.core.win;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Most points wins once the match runs its course, with equal points sharing a place and the places
 * after skipping ahead (1, 1, 3). It is decided early only when at most one participant is left.
 */
public final class MostPoints implements WinCondition {

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        return roster.alive().size() > 1 ? Optional.empty() : Optional.of(finalResult(roster, scores));
    }

    /** Those still alive rank above those eliminated, whatever their points. */
    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        var placements = new ArrayList<Placement>();
        rank(roster.alive(), scores, placements);
        rank(roster.eliminationOrder(), scores, placements);
        return new MatchResult(placements);
    }

    private static void rank(List<Participant> participants, Scores scores, List<Placement> placements) {
        var byPoints = new TreeMap<Integer, List<UUID>>(Comparator.reverseOrder());
        for (Participant participant : participants) {
            byPoints.computeIfAbsent(scores.of(participant.id()), points -> new ArrayList<>()).add(participant.id());
        }
        int place = placements.stream().mapToInt(placement -> placement.participants().size()).sum() + 1;
        for (List<UUID> tied : byPoints.values()) {
            placements.add(new Placement(place, tied));
            place += tied.size();
        }
    }
}
