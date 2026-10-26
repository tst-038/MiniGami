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

    /** Features per phase id; phases without an entry have none. */
    Map<String, List<Feature>> features();

    /** A fresh one per match. */
    WinCondition winCondition();

    /** How many deaths put a player out, unless the mode sets its own; 0 means they always respawn. */
    default int lives() {
        return 0;
    }
}
