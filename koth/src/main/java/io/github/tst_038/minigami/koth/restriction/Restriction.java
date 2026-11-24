package io.github.tst_038.minigami.koth.restriction;

import java.util.Optional;

/**
 * A condition a scheduled start must meet. A new one is a new class, a field on {@link Situation} if it
 * needs facts nobody gives yet, and a line in the config reader.
 */
public interface Restriction {

    /** Empty when the start may go ahead. */
    Optional<Refusal> check(Situation situation);
}
