package io.github.tst_038.minigami.itemrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FirstDepositTest {

    private final FirstDeposit win = new FirstDeposit();
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
    void nothingIsDecidedUntilSomeoneDeposits() {
        assertTrue(win.check(roster, scores).isEmpty());
    }

    @Test
    void theFirstDepositWinsAtOnce() {
        scores.add(bob, 1);
        roster.eliminate(carol);

        var result = win.check(roster, scores).orElseThrow();

        assertEquals(List.of(new Placement(1, List.of(bob)), new Placement(2, List.of(alice)), new Placement(3, List.of(carol))), result.placements());
    }

    @Test
    void timeUpWithoutADepositHasNoWinnerAtAll() {
        assertTrue(win.finalResult(roster, scores).placements().isEmpty());
        roster.eliminate(carol);
        assertTrue(win.finalResult(roster, scores).placements().isEmpty());
    }

    @Test
    void theLastOneStandingWinsWithoutDepositing() {
        roster.eliminate(alice);
        roster.eliminate(carol);

        var result = win.check(roster, scores).orElseThrow();

        assertEquals(List.of(new Placement(1, List.of(bob)), new Placement(2, List.of(carol)), new Placement(3, List.of(alice))), result.placements());
    }

    @Test
    void aTeamWinsWhenAnyMemberDeposits() {
        roster.assignTeam(alice, "red");
        roster.assignTeam(bob, "red");
        roster.assignTeam(carol, "blue");
        scores.add(bob, 1);

        var first = win.check(roster, scores).orElseThrow().placements().getFirst();

        assertEquals(1, first.place());
        assertEquals(Set.of(alice, bob), Set.copyOf(first.participants()));
    }
}
