package io.github.tst_038.minigami.core.match;

import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.phase.PhaseMachine;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One running round. Owns its scope, clock, roster, scores and phases, and asks the win condition
 * after every elimination and score change whether it is decided. Once decided it skips to its last
 * phase. Reaching the last phase undecided, for example when time runs out, the win condition gives
 * the final result. When the last phase ends, the match is finished and its owner reads the result
 * and calls {@link #dispose()}.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class Match {

    private final Scope scope = new Scope();
    private final MatchClock clock = new MatchClock();
    private final Roster roster = new Roster();
    private final Scores scores = new Scores();
    private final PhaseMachine phases;
    private final WinCondition winCondition;
    private MatchResult result;

    /** @param phaseListener told when each phase starts, e.g. to enable that phase's features */
    public Match(List<Phase> phases, WinCondition winCondition, PhaseListener phaseListener) {
        this.phases = new PhaseMachine(phases, clock, scope, new PhaseListener() {
            @Override
            public void onEnter(Phase phase, Scope phaseScope) {
                // A single phase is also the first one; that match is only decided when it runs out.
                if (phases.size() > 1 && phase.equals(phases.getLast())) {
                    decideAsThingsStand();
                }
                phaseListener.onEnter(phase, phaseScope);
            }

            @Override
            public void onFinish() {
                decideAsThingsStand();
                phaseListener.onFinish();
            }
        });
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
            winCondition.check(roster, scores).ifPresent(this::end);
        }
    }

    /**
     * Moves a participant to another team mid-match, for example a caught hider who becomes a seeker,
     * and ends the match if that decides it.
     */
    public void switchTeam(UUID id, String team) {
        roster.assignTeam(id, team);
        if (result == null) {
            winCondition.check(roster, scores).ifPresent(this::end);
        }
    }

    /** Gives a participant points, or takes them away when negative, and ends the match if that decides it. */
    public void addPoints(UUID id, int points) {
        scores.add(id, points);
        if (result == null) {
            winCondition.check(roster, scores).ifPresent(this::end);
        }
    }

    /**
     * Skips to a later phase, for example by an admin. Reaching the last phase undecided asks the
     * win condition for the final result, as running out of time would.
     *
     * @throws IllegalArgumentException if there is no such phase after the current one
     */
    public void skipTo(String phaseId) {
        phases.skipTo(phaseId);
    }

    /** The phases after the current one. */
    public List<Phase> laterPhases() {
        return phases.later();
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

    /**
     * Ends the match now with the result the win condition gives as things stand, as if time ran
     * out, for example when an admin ends it.
     *
     * @throws IllegalStateException if the match already has a result
     */
    public void endNow() {
        end(winCondition.finalResult(roster, scores));
    }

    /** The result once decided, at the latest when the last phase starts. */
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

    public Scores scores() {
        return scores;
    }

    private void decideAsThingsStand() {
        if (result == null) {
            result = winCondition.finalResult(roster, scores);
        }
    }
}
