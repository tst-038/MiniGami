package io.github.tst_038.minigami.king;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KingRankingTest {

    private final KingRanking ranking = new KingRanking();
    private final Roster roster = new Roster();
    private final Scores scores = new Scores();
    private final UUID redKing = member("red", "king");
    private final UUID redGuard = member("red", "guard");
    private final UUID redGuard2 = member("red", "guard");
    private final UUID blueKing = member("blue", "king");
    private final UUID blueGuard = member("blue", "guard");
    private final UUID blueGuard2 = member("blue", "guard");

    private UUID member(String team, String rank) {
        var id = UUID.randomUUID();
        roster.add(id);
        roster.assignTeam(id, team);
        roster.assignRank(id, rank);
        return id;
    }

    private List<Placement> places() {
        return ranking.finalResult(roster, scores).placements();
    }

    @Test
    void theMatchGoesOnWhileBothTeamsHaveSomeoneAlive() {
        roster.eliminate(redKing);
        roster.eliminate(blueGuard);

        assertTrue(ranking.check(roster, scores).isEmpty());
    }

    @Test
    void theLastTeamWithSomeoneAliveWinsAndTheOtherFollows() {
        roster.eliminate(redKing);
        roster.eliminate(redGuard);
        roster.eliminate(redGuard2);

        var result = ranking.check(roster, scores).orElseThrow();

        assertEquals(List.of(new Placement(1, List.of(blueKing, blueGuard, blueGuard2)), new Placement(2, List.of(redKing, redGuard, redGuard2))),
                result.placements());
    }

    @Test
    void aLivingKingBeatsMoreSurvivorsAndKills() {
        roster.eliminate(blueKing);
        scores.add(blueGuard, 5);

        assertEquals(1, places().getFirst().place());
        assertTrue(places().getFirst().participants().contains(redKing));
    }

    @Test
    void withKingsEqualMoreLivingMembersWin() {
        roster.eliminate(redGuard);
        scores.add(redKing, 9);

        assertTrue(places().getFirst().participants().contains(blueKing));
        assertEquals(2, places().get(1).place());
    }

    @Test
    void withMembersEqualMoreKillsWin() {
        scores.add(blueGuard, 1);
        scores.add(blueKing, 1);
        scores.add(redGuard, 1);

        assertTrue(places().getFirst().participants().contains(blueKing));
    }

    @Test
    void teamsEqualOnEverythingShareFirstPlace() {
        var result = places();

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().place());
        assertEquals(6, result.getFirst().participants().size());
    }

    @Test
    void twoKingsDeadIsDecidedByMembersThenKills() {
        roster.eliminate(redKing);
        roster.eliminate(blueKing);
        roster.eliminate(blueGuard);

        assertTrue(places().getFirst().participants().contains(redGuard));
    }
}
