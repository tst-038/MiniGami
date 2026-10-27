package io.github.tst_038.minigami.core.win;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MostPointsTest {

    private final WinCondition winCondition = new MostPoints();
    private final Roster roster = new Roster();
    private final Scores scores = new Scores();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID dave = UUID.randomUUID();

    {
        roster.add(alice);
        roster.add(bob);
        roster.add(carol);
        roster.add(dave);
    }

    @Test
    void pointsAloneDoNotDecideTheMatch() {
        scores.add(alice, 100);

        assertTrue(winCondition.check(roster, scores).isEmpty());
    }

    @Test
    void mostPointsWinsAndEqualPointsShareAPlace() {
        scores.add(alice, 3);
        scores.add(bob, 7);
        scores.add(carol, 3);
        scores.add(carol, 2);
        scores.add(carol, -2);

        var result = winCondition.finalResult(roster, scores);

        assertEquals(List.of(
                new Placement(1, List.of(bob)),
                new Placement(2, List.of(alice, carol)),
                new Placement(4, List.of(dave))), result.placements());
    }

    @Test
    void thoseEliminatedPlaceBelowThoseLeftWhateverTheirPoints() {
        scores.add(alice, 10);
        roster.eliminate(alice);
        roster.eliminate(bob);
        roster.eliminate(carol);

        var result = winCondition.check(roster, scores).orElseThrow();

        assertEquals(List.of(
                new Placement(1, List.of(dave)),
                new Placement(2, List.of(alice)),
                new Placement(3, List.of(bob, carol))), result.placements());
    }
}
