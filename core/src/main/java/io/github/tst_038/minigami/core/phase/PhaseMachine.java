package io.github.tst_038.minigami.core.phase;

import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.scope.Scope;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Runs the phases of a match in order. Each phase gets its own child scope of the match scope,
 * which is closed when the phase ends, and a timed phase advances on its own via the match clock.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class PhaseMachine {

    private final List<Phase> phases;
    private final MatchClock clock;
    private final Scope matchScope;
    private final PhaseListener listener;
    private int index = -1;
    private Scope phaseScope;
    private boolean finished;

    /** @throws IllegalArgumentException if there are no phases or two share an id */
    public PhaseMachine(List<Phase> phases, MatchClock clock, Scope matchScope, PhaseListener listener) {
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("A match needs at least one phase");
        }
        var ids = new HashSet<String>();
        for (Phase phase : phases) {
            if (!ids.add(phase.id())) {
                throw new IllegalArgumentException("Duplicate phase id: " + phase.id());
            }
        }
        this.phases = List.copyOf(phases);
        this.clock = clock;
        this.matchScope = matchScope;
        this.listener = listener;
    }

    /** Enters the first phase. */
    public void start() {
        if (index >= 0) {
            throw new IllegalStateException("Already started");
        }
        enter(0);
    }

    /**
     * Ends the current phase and enters the next one, or finishes after the last phase. The next
     * phase is entered even if closing the current phase scope fails; that failure is rethrown.
     */
    public void advance() {
        requireRunning();
        moveTo(index + 1);
    }

    /**
     * Skips straight to the last phase, where a match wraps up once it is decided. Finishes if the
     * last phase is already running.
     */
    public void skipToEnd() {
        requireRunning();
        int last = phases.size() - 1;
        moveTo(index == last ? phases.size() : last);
    }

    /** The phase that is running, or empty before {@link #start()} and after the last phase. */
    public Optional<Phase> current() {
        return index < 0 || finished ? Optional.empty() : Optional.of(phases.get(index));
    }

    public boolean isFinished() {
        return finished;
    }

    private void requireRunning() {
        if (index < 0) {
            throw new IllegalStateException("Not started");
        }
        if (finished) {
            throw new IllegalStateException("Already finished");
        }
    }

    private void moveTo(int next) {
        try {
            phaseScope.close();
        } finally {
            if (next < phases.size()) {
                enter(next);
            } else {
                finished = true;
                phaseScope = null;
                listener.onFinish();
            }
        }
    }

    private void enter(int next) {
        index = next;
        phaseScope = matchScope.child();
        Phase phase = phases.get(next);
        // Scheduled before onEnter, so a listener that advances right away also cancels it.
        if (phase.isTimed()) {
            phaseScope.register(clock.schedule(phase.durationTicks(), this::advance));
        }
        listener.onEnter(phase, phaseScope);
    }
}
