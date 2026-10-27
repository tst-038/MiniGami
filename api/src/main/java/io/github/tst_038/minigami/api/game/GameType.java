package io.github.tst_038.minigami.api.game;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;

/**
 * What a game is: its phases, the features active in each, and how a winner is decided. The first
 * phase is where players gather; joining is only possible then.
 */
public interface GameType {

    String id();

    List<Phase> phases();

    /** Features per phase id that are part of the game itself; phases without an entry have none. */
    Map<String, List<Feature>> features();

    /**
     * Configurable features the game turns on unless a mode says otherwise, in the shape of a mode's
     * {@code features} section: phase id, then feature id, then its settings, e.g.
     * {@code Map.of("playing", Map.of("kit", Map.of("items", List.of("diamond_shovel"))))}. A mode
     * replaces single settings, or turns a feature off with {@code <feature>: false}.
     */
    default Map<String, ?> defaultFeatures() {
        return Map.of();
    }

    /** A fresh one per match. */
    WinCondition winCondition();

    /** How many deaths put a player out, unless the mode sets its own; 0 means they always respawn. */
    default int lives() {
        return 0;
    }
}
