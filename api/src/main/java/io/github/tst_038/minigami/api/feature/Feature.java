package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
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

    /**
     * Whether players break or place blocks themselves, so the game must give them a mode that can (not adventure).
     * False for what the match does on its own, such as generating land, filling containers or breaking blocks under players.
     */
    default boolean needsPlayersToBuild() {
        return false;
    }

    /** Whether it fills the arena's loot containers, so a game with it wants an arena that has some. */
    default boolean usesLootPoints() {
        return false;
    }

    /** The groups of arena regions it relies on, such as {@code quarter}. A game with it only plays in arenas that have a region of each. */
    default List<String> regionGroups() {
        return List.of();
    }

    /**
     * Checks that the arena suits this feature in ways the engine can't know, such as regions too
     * small for what it builds. Blocking findings keep the arena from hosting the game; the codes need a
     * text in the messages' {@code diagnosis} section.
     */
    default void diagnose(ArenaLayout arena, Diagnosis diagnosis) {
    }

    /** The arena regions it relies on, by name. A game with it only plays in arenas that have them all. */
    default List<String> regions() {
        return List.of();
    }
}
