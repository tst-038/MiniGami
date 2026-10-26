package io.github.tst_038.minigami.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LastStandingTest {

    private final WinCondition winCondition = new LastStanding();
    private final Roster roster = new Roster();
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

        assertTrue(winCondition.check(roster).isEmpty());
    }

    @Test
    void survivorWinsAndTheRestArePlacedByLastEliminatedFirst() {
        roster.eliminate(carol);
        roster.eliminate(alice);

        var result = winCondition.check(roster).orElseThrow();

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

        var result = winCondition.check(roster).orElseThrow();

        assertEquals(List.of(
                new Placement(1, List.of(alice)),
                new Placement(2, List.of(carol)),
                new Placement(3, List.of(bob))), result.placements());
    }
}
