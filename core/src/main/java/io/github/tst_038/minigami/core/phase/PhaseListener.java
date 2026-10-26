package io.github.tst_038.minigami.core.phase;

import io.github.tst_038.minigami.core.scope.Scope;

/** Reacts to a {@link PhaseMachine} moving through its phases. */
public interface PhaseListener {

    /** A phase has started. Whatever is registered in {@code scope} is released when the phase ends. */
    default void onEnter(Phase phase, Scope scope) {
    }

    /** The last phase has ended. */
    default void onFinish() {
    }
}
