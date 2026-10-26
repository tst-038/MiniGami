package io.github.tst_038.minigami.core.match;

import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.phase.PhaseMachine;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One running round. Owns its scope, clock, roster and phases, and asks the win condition after
 * every elimination whether it is decided. Once decided it skips to its last phase; when that ends,
 * the match is finished and its owner reads the result and calls {@link #dispose()}.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class Match {

    private final Scope scope = new Scope();
    private final MatchClock clock = new MatchClock();
    private final Roster roster = new Roster();
    private final PhaseMachine phases;
    private final WinCondition winCondition;
    private MatchResult result;

    /** @param phaseListener told when each phase starts, e.g. to enable that phase's features */
    public Match(List<Phase> phases, WinCondition winCondition, PhaseListener phaseListener) {
        this.phases = new PhaseMachine(phases, clock, scope, phaseListener);
        this.winCondition = winCondition;
    }

    public void start() {
        phases.start();
    }

    /** Advances the match time by one tick; call once per server tick. */
    public void tick() {
        clock.tick();
    }

    public void advancePhase() {
        phases.advance();
    }

    /** Eliminates a participant and ends the match if that decides it. Repeats are ignored. */
    public void eliminate(UUID id) {
        if (roster.eliminate(id) && result == null) {
            winCondition.check(roster).ifPresent(this::end);
        }
    }

    /**
     * Ends the match with the given result, for example from a time limit or an admin, and skips
     * to the last phase.
     *
     * @throws IllegalStateException if the match already has a result
     */
    public void end(MatchResult result) {
        if (this.result != null) {
            throw new IllegalStateException("Match already has a result");
        }
        this.result = result;
        phases.skipToEnd();
    }

    /** The result once decided. Stays empty if the phases ran out without a winner. */
    public Optional<MatchResult> result() {
        return Optional.ofNullable(result);
    }

    public Optional<Phase> currentPhase() {
        return phases.current();
    }

    /** True once the last phase has ended or the match was disposed. */
    public boolean isFinished() {
        return phases.isFinished() || scope.isClosed();
    }

    /** Releases everything the match owns. Calling it again does nothing. */
    public void dispose() {
        scope.close();
    }

    public Scope scope() {
        return scope;
    }

    public MatchClock clock() {
        return clock;
    }

    public Roster roster() {
        return roster;
    }
}
