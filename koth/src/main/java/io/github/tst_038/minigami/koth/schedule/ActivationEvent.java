package io.github.tst_038.minigami.koth.schedule;

import io.github.tst_038.minigami.koth.restriction.Refusal;
import java.time.Duration;
import java.time.LocalDateTime;

/** Something {@link Activations} wants the server to act on. */
public sealed interface ActivationEvent {

    /** A start is {@code before} away; tell the players. */
    record Warning(Duration before, LocalDateTime start) implements ActivationEvent {
    }

    /** KOTH should begin now and run until the window ends. {@code forced} is true for a staff start. */
    record Started(Window window, boolean forced) implements ActivationEvent {
    }

    /** A window came and a restriction said no, so it won't run. */
    record Skipped(Window window, Refusal refusal) implements ActivationEvent {
    }

    /** KOTH should end now. */
    record Ended(Window window, EndReason reason) implements ActivationEvent {
    }
}
