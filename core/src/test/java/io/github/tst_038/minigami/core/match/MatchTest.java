package io.github.tst_038.minigami.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    private Match match(Phase... phases) {
        return match(new LastManStanding(), phases);
    }

    private Match match(WinCondition winCondition, Phase... phases) {
        var match = new Match(List.of(phases), winCondition, new PhaseListener() {});
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
    void anUntimedPhaseCanSayHowLongItTakesUntilItEnds() {
        var match = match(Phase.untimed("voting"), Phase.untimed("ending"));
        match.start();
        assertEquals(0, match.phaseLengthTicks());

        match.expectPhaseToTake(100);
        assertEquals(100, match.phaseLengthTicks());

        match.advancePhase();
        assertEquals(0, match.phaseLengthTicks());
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
    void phaseElapsedTicksStartOverWithEachPhase() {
        var match = match(Phase.timed("waiting", 10), Phase.untimed("playing"));
        match.start();
        tick(match, 4);
        assertEquals(4, match.phaseElapsedTicks());

        tick(match, 8);
        assertEquals("playing", phase(match));
        assertEquals(2, match.phaseElapsedTicks());
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
    void switchingTeamCanDecideTheMatch() {
        var match = match(Phase.untimed("playing"), Phase.timed("ending", 3));
        match.roster().assignTeam(alice, "seeker");
        match.roster().assignTeam(bob, "hider");
        match.roster().assignTeam(carol, "hider");
        match.start();

        match.switchTeam(bob, "seeker");
        assertTrue(match.result().isEmpty());

        match.switchTeam(carol, "seeker");

        assertEquals(List.of(alice, bob, carol), match.result().orElseThrow().placements().getFirst().participants());
        assertEquals("ending", phase(match));
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
    void skippingToTheLastPhaseDecidesTheMatchAsThingsStand() {
        var match = match(Phase.untimed("playing"), Phase.untimed("deathmatch"), Phase.untimed("ending"));
        match.start();
        match.eliminate(carol);

        match.skipTo("deathmatch");
        assertTrue(match.result().isEmpty());
        match.skipTo("ending");

        assertEquals(new Placement(1, List.of(alice, bob)), match.result().orElseThrow().placements().getFirst());
    }

    @Test
    void endingNowAsksForTheFinalResult() {
        var match = match(Phase.untimed("playing"), Phase.untimed("ending"));
        match.start();
        match.eliminate(carol);

        match.endNow();

        assertEquals("ending", phase(match));
        assertEquals(new Placement(1, List.of(alice, bob)), match.result().orElseThrow().placements().getFirst());
        assertThrows(IllegalStateException.class, match::endNow);
    }

    @Test
    void theResultListenerIsToldOnceBeforeTheLastPhaseStarts() {
        var match = match(Phase.untimed("playing"), Phase.timed("ending", 3));
        var told = new ArrayList<String>();
        match.onResult(result -> told.add(phase(match) + " " + result.placements().getFirst().participants()));
        match.start();

        match.eliminate(carol);
        match.eliminate(alice);
        match.eliminate(bob);

        assertEquals(List.of("playing " + List.of(bob)), told);
        assertEquals("ending", phase(match));
    }

    @Test
    void runningOutOfTimeTellsTheResultListenerAndStoppingDoesNot() {
        var told = new ArrayList<MatchResult>();
        var timedOut = match(Phase.timed("playing", 2), Phase.untimed("ending"));
        timedOut.onResult(told::add);
        timedOut.start();
        tick(timedOut, 2);
        var stopped = match(Phase.timed("playing", 2), Phase.untimed("ending"));
        stopped.onResult(told::add);
        stopped.start();
        stopped.dispose();

        assertEquals(List.of(timedOut.result().orElseThrow()), told);
    }

    @Test
    void reachingTheLastPhaseUndecidedAsksForTheFinalResult() {
        var match = match(Phase.timed("playing", 2), Phase.untimed("ending"));
        match.start();
        match.eliminate(carol);

        tick(match, 2);

        assertEquals("ending", phase(match));
        assertEquals(new Placement(1, List.of(alice, bob)), match.result().orElseThrow().placements().getFirst());
    }

    @Test
    void aSinglePhaseIsOnlyDecidedWhenItRunsOut() {
        var match = match(Phase.timed("playing", 2));
        match.start();
        assertTrue(match.result().isEmpty());

        tick(match, 2);

        assertTrue(match.isFinished());
        assertEquals(new Placement(1, List.of(alice, bob, carol)), match.result().orElseThrow().placements().getFirst());
    }

    @Test
    void pointsCanDecideTheMatch() {
        var firstToThree = new WinCondition() {
            @Override
            public Optional<MatchResult> check(Roster roster, Scores scores) {
                return scores.of(bob) >= 3 ? Optional.of(finalResult(roster, scores)) : Optional.empty();
            }

            @Override
            public MatchResult finalResult(Roster roster, Scores scores) {
                return new MatchResult(List.of(new Placement(1, List.of(bob))));
            }
        };
        var match = match(firstToThree, Phase.untimed("playing"), Phase.untimed("ending"));
        match.start();

        match.addPoints(bob, 2);
        assertTrue(match.result().isEmpty());
        match.addPoints(bob, 1);

        assertEquals("ending", phase(match));
        assertEquals(3, match.scores().of(bob));
        assertTrue(match.result().isPresent());
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
