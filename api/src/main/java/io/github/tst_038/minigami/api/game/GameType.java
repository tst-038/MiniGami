package io.github.tst_038.minigami.api.game;

import io.github.tst_038.minigami.api.config.ConfigReader;
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

    /**
     * Features per phase id that are part of the game itself; phases without an entry have none.
     * Asked once per match, so features made here may share state across the phases of that match.
     */
    Map<String, List<Feature>> features();

    /**
     * The game with the settings of its own a mode gives it, read from the mode's top level, such as
     * a build time. Games without settings keep the default, so such keys are unknown keys.
     */
    default GameType configure(ConfigReader mode) {
        return this;
    }

    /** A fresh one per match. */
    WinCondition winCondition();

    /** How many deaths put a player out, unless the mode sets its own; 0 means they always respawn. */
    default int lives() {
        return 0;
    }
}
