package io.github.tst_038.minigami.api.feature;

import java.util.List;

/**
 * One reusable piece of game behaviour, such as void kills or no hunger, active during the phases
 * a game assigns it to.
 *
 * <p>There is no {@code disable()}: everything a feature sets up goes through its
 * {@link MatchContext}, and is released automatically when the phase ends.
 */
public interface Feature {

    /** Called when one of the feature's phases starts. Subscribe to events and schedule tasks here. */
    void enable(MatchContext context);

    /**
     * Whether it changes the arena's blocks. Nothing puts them back but the arena's saved map, pasted
     * after every match, so a game with such a feature only plays in arenas that have one.
     */
    default boolean changesBlocks() {
        return false;
    }

    /** The arena regions it relies on, by name. A game with it only plays in arenas that have them all. */
    default List<String> regions() {
        return List.of();
    }
}
