package io.github.tst_038.minigami.core.win;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SurvivalTest {

    private final WinCondition winCondition = new Survival("hider");
    private final Roster roster = new Roster();
    private final Scores scores = new Scores();
    private final UUID seeker = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    {
        roster.add(seeker);
        roster.assignTeam(seeker, "seeker");
        for (var hider : List.of(alice, bob, carol)) {
            roster.add(hider);
            roster.assignTeam(hider, "hider");
        }
    }

    @Test
    void notDecidedWhileAHiderIsLeft() {
        roster.eliminate(alice);
        roster.eliminate(bob);

        assertTrue(winCondition.check(roster, scores).isEmpty());
    }

    @Test
    void seekersWinOnceEveryHiderIsCaughtAndTheLastCaughtComesNext() {
        roster.eliminate(bob);
        roster.eliminate(alice);
        roster.eliminate(carol);

        assertEquals(List.of(
                new Placement(1, List.of(seeker)),
                new Placement(2, List.of(carol)),
                new Placement(3, List.of(alice)),
                new Placement(4, List.of(bob))), winCondition.check(roster, scores).orElseThrow().placements());
    }

    @Test
    void hidersLeftWinWhenTimeRunsOut() {
        roster.eliminate(bob);

        assertEquals(List.of(
                new Placement(1, List.of(alice, carol)),
                new Placement(2, List.of(seeker)),
                new Placement(3, List.of(bob))), winCondition.finalResult(roster, scores).placements());
    }

    @Test
    void hidersWinAtOnceWhenNoSeekerIsLeft() {
        roster.eliminate(seeker);

        assertEquals(new Placement(1, List.of(alice, bob, carol)), winCondition.check(roster, scores).orElseThrow().placements().getFirst());
    }
}
