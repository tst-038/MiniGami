package io.github.tst_038.minigami.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.win.LastManStanding;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    private Match match(Phase... phases) {
        var match = new Match(List.of(phases), new LastManStanding(), new PhaseListener() {});
        match.roster().add(alice);
        match.roster().add(bob);
        match.roster().add(carol);
        return match;
    }

    private void tick(Match match, int times) {
        for (int i = 0; i < times; i++) {
            match.tick();
        }
    }

    private String phase(Match match) {
        return match.currentPhase().orElseThrow().id();
    }

    @Test
    void startEntersTheFirstPhaseAndAdvancePhaseMovesOn() {
        var match = match(Phase.untimed("waiting"), Phase.untimed("playing"));

        match.start();
        assertEquals("waiting", phase(match));

        match.advancePhase();
        assertEquals("playing", phase(match));
    }

    @Test
    void lastSurvivorDecidesTheMatchAndSkipsToTheEndingPhase() {
        var match = match(Phase.untimed("playing"), Phase.untimed("deathmatch"), Phase.timed("ending", 3));
        match.start();

        match.eliminate(carol);
        assertTrue(match.result().isEmpty());

        match.eliminate(alice);

        assertEquals(new Placement(1, List.of(bob)), match.result().orElseThrow().placements().getFirst());
        assertEquals("ending", phase(match));
        assertFalse(match.isFinished());

        tick(match, 3);
        assertTrue(match.isFinished());
    }

    @Test
    void singlePhaseMatchFinishesAsSoonAsItIsDecided() {
        var match = match(Phase.untimed("playing"));
        match.start();

        match.eliminate(alice);
        match.eliminate(bob);

        assertTrue(match.isFinished());
    }

    @Test
    void eliminationsAfterTheResultDoNotChangeIt() {
        var match = match(Phase.untimed("playing"), Phase.untimed("ending"));
        match.start();
        match.eliminate(alice);
        match.eliminate(bob);
        var result = match.result().orElseThrow();

        match.eliminate(carol);

        assertEquals(result, match.result().orElseThrow());
    }

    @Test
    void endingByHandUsesTheGivenResult() {
        var match = match(Phase.untimed("playing"), Phase.untimed("ending"));
        match.start();
        var result = new MatchResult(List.of(new Placement(1, List.of(alice, bob, carol))));

        match.end(result);

        assertEquals(result, match.result().orElseThrow());
        assertEquals("ending", phase(match));
        assertThrows(IllegalStateException.class, () -> match.end(result));
    }

    @Test
    void runningOutOfPhasesWithoutAWinnerFinishesWithoutResult() {
        var match = match(Phase.timed("playing", 2));
        match.start();

        tick(match, 2);

        assertTrue(match.isFinished());
        assertTrue(match.result().isEmpty());
    }

    @Test
    void disposeReleasesEverythingTheMatchOwns() {
        var match = match(Phase.untimed("playing"));
        match.start();
        var released = new boolean[1];
        match.scope().register(() -> released[0] = true);

        match.dispose();
        match.dispose();

        assertTrue(released[0]);
        assertTrue(match.scope().isClosed());
        assertTrue(match.isFinished());
    }
}
