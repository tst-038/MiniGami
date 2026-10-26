package io.github.tst_038.minigami.core.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.scope.Scope;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseMachineTest {

    private final MatchClock clock = new MatchClock();
    private final Scope matchScope = new Scope();
    private final List<String> entered = new ArrayList<>();
    private final List<Scope> phaseScopes = new ArrayList<>();
    private int finished;

    private final PhaseListener listener = new PhaseListener() {
        @Override
        public void onEnter(Phase phase, Scope scope) {
            entered.add(phase.id());
            phaseScopes.add(scope);
        }

        @Override
        public void onFinish() {
            finished++;
        }
    };

    private PhaseMachine machine(Phase... phases) {
        return new PhaseMachine(List.of(phases), clock, matchScope, listener);
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) {
            clock.tick();
        }
    }

    @Test
    void startEntersTheFirstPhase() {
        var machine = machine(Phase.untimed("waiting"), Phase.untimed("playing"));

        machine.start();

        assertEquals(List.of("waiting"), entered);
        assertEquals("waiting", machine.current().orElseThrow().id());
    }

    @Test
    void advanceClosesThePhaseScopeAndEntersTheNext() {
        var machine = machine(Phase.untimed("waiting"), Phase.untimed("playing"));
        machine.start();

        machine.advance();

        assertEquals(List.of("waiting", "playing"), entered);
        assertTrue(phaseScopes.get(0).isClosed());
        assertFalse(phaseScopes.get(1).isClosed());
    }

    @Test
    void timedPhaseAdvancesOnItsOwn() {
        var machine = machine(Phase.timed("countdown", 3), Phase.untimed("playing"));
        machine.start();

        tick(2);
        assertEquals("countdown", machine.current().orElseThrow().id());

        tick(1);
        assertEquals("playing", machine.current().orElseThrow().id());
    }

    @Test
    void advancingEarlyCancelsTheAutoAdvance() {
        var machine = machine(Phase.timed("countdown", 3), Phase.untimed("playing"), Phase.untimed("ending"));
        machine.start();

        machine.advance();
        tick(5);

        assertEquals("playing", machine.current().orElseThrow().id());
    }

    @Test
    void advancingPastTheLastPhaseFinishes() {
        var machine = machine(Phase.untimed("playing"), Phase.timed("ending", 2));
        machine.start();
        machine.advance();

        tick(2);

        assertEquals(1, finished);
        assertTrue(machine.isFinished());
        assertTrue(machine.current().isEmpty());
        assertTrue(phaseScopes.get(1).isClosed());
    }

    @Test
    void advancingDuringOnEnterSkipsThatPhase() {
        var phases = List.of(Phase.timed("skipped", 5), Phase.untimed("playing"));
        var machine = new PhaseMachine[1];
        machine[0] = new PhaseMachine(phases, clock, matchScope, new PhaseListener() {
            @Override
            public void onEnter(Phase phase, Scope scope) {
                entered.add(phase.id());
                if (phase.id().equals("skipped")) {
                    machine[0].advance();
                }
            }

            @Override
            public void onFinish() {
                finished++;
            }
        });

        machine[0].start();
        tick(10);

        assertEquals(List.of("skipped", "playing"), entered);
        assertEquals("playing", machine[0].current().orElseThrow().id());
    }

    @Test
    void failingCleanupStillAdvances() {
        var machine = machine(Phase.untimed("playing"), Phase.untimed("ending"));
        machine.start();
        phaseScopes.get(0).register(() -> { throw new IllegalArgumentException("boom"); });

        assertThrows(IllegalStateException.class, machine::advance);

        assertEquals("ending", machine.current().orElseThrow().id());
    }

    @Test
    void closingTheMatchScopeClosesTheCurrentPhaseScope() {
        machine(Phase.untimed("playing")).start();

        matchScope.close();

        assertTrue(phaseScopes.get(0).isClosed());
    }

    @Test
    void misuseFails() {
        var machine = machine(Phase.untimed("playing"));
        assertThrows(IllegalStateException.class, machine::advance);

        machine.start();
        assertThrows(IllegalStateException.class, machine::start);

        machine.advance();
        assertThrows(IllegalStateException.class, machine::advance);
    }

    @Test
    void rejectsInvalidPhases() {
        assertThrows(IllegalArgumentException.class, this::machine);
        assertThrows(IllegalArgumentException.class, () -> machine(Phase.untimed("a"), Phase.untimed("a")));
        assertThrows(IllegalArgumentException.class, () -> Phase.timed("a", 0));
        assertThrows(IllegalArgumentException.class, () -> Phase.untimed(" "));
    }
}
