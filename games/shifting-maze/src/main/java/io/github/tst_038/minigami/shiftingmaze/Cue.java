package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;

/** One way of telling players that a wall is about to move. Called every few ticks while the warning lasts. */
interface Cue {

    /**
     * @param opening whether the wall is about to open (it stands now) rather than close (it is open now)
     * @param ticksLeft ticks until the shift happens
     */
    void tick(MatchContext context, Span wall, boolean opening, int ticksLeft);

    /** Undoes whatever the cue left behind on the wall. Called once per wall when the shift is applied, dropped or the match ends. */
    default void stop(MatchContext context, Span wall) {
    }
}
