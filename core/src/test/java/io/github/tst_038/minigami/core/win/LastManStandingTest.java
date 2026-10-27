package io.github.tst_038.minigami.core.win;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LastManStandingTest {

    private final WinCondition winCondition = new LastManStanding();
    private final Roster roster = new Roster();
    private final Scores scores = new Scores();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    {
        roster.add(alice);
        roster.add(bob);
        roster.add(carol);
    }

    @Test
    void noResultWhileMoreThanOneIsAlive() {
        roster.eliminate(bob);

        assertTrue(winCondition.check(roster, scores).isEmpty());
    }

    @Test
    void survivorWinsAndTheRestArePlacedByLastEliminatedFirst() {
        roster.eliminate(carol);
        roster.eliminate(alice);

        var result = winCondition.check(roster, scores).orElseThrow();

        assertEquals(List.of(
                new Placement(1, List.of(bob)),
                new Placement(2, List.of(alice)),
                new Placement(3, List.of(carol))), result.placements());
    }

    @Test
    void lastEliminatedWinsWhenNobodyIsLeft() {
        roster.eliminate(bob);
        roster.eliminate(carol);
        roster.eliminate(alice);

        var result = winCondition.check(roster, scores).orElseThrow();

        assertEquals(List.of(
                new Placement(1, List.of(alice)),
                new Placement(2, List.of(carol)),
                new Placement(3, List.of(bob))), result.placements());
    }

    @Test
    void finalResultLetsEveryoneStillAliveShareFirstPlace() {
        roster.eliminate(alice);

        var result = winCondition.finalResult(roster, scores);

        assertEquals(List.of(
                new Placement(1, List.of(bob, carol)),
                new Placement(3, List.of(alice))), result.placements());
    }
}
