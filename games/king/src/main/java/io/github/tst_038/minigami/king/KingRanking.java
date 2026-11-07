package io.github.tst_038.minigami.king;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

/**
 * The match is over once at most one team has someone alive. When time is up instead, the teams still in are ranked by
 * whether their King lives, then by how many of them live, then by their kills; teams equal on all three share a place.
 * Teams that are out follow in reverse order of going out.
 */
final class KingRanking implements WinCondition {

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        return roster.teamsLeft().size() > 1 ? Optional.empty() : Optional.of(finalResult(roster, scores));
    }

    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        var standing = Comparator.<MatchTeam, Boolean>comparing(KingRanking::kingLives)
                .thenComparingLong(KingRanking::alive)
                .thenComparingInt(team -> kills(team, scores));
        var left = roster.teamsLeft().stream().sorted(standing.reversed()).toList();
        var placements = new ArrayList<Placement>();
        for (int i = 0; i < left.size();) {
            // Teams equal on everything share a place, and the next one skips the places they took.
            int end = i + 1;
            while (end < left.size() && standing.compare(left.get(i), left.get(end)) == 0) {
                end++;
            }
            placements.add(new Placement(i + 1, left.subList(i, end).stream().flatMap(team -> team.ids().stream()).toList()));
            i = end;
        }
        int out = left.size() + 1;
        for (var team : roster.teamsOut().reversed()) {
            placements.add(new Placement(out++, team.ids()));
        }
        return new MatchResult(placements);
    }

    private static boolean kingLives(MatchTeam team) {
        return team.members().stream().anyMatch(member -> member.isAlive() && member.rank().filter(King.KING::equals).isPresent());
    }

    private static long alive(MatchTeam team) {
        return team.members().stream().filter(Participant::isAlive).count();
    }

    private static int kills(MatchTeam team, Scores scores) {
        return team.ids().stream().mapToInt(scores::of).sum();
    }
}
