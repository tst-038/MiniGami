package io.github.tst_038.minigami.paper.mode;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.List;
import java.util.Map;

/**
 * A playable variant of a game, as set up in {@code modes/<id>.yml}: its player limits, where
 * players wait, the arenas it can be played in, and the configured features per phase.
 *
 * @param name display name, in MiniMessage
 * @param features feature instances are shared by every match of the mode, so they keep no match state themselves
 */
public record Mode(String id, GameType game, String name, int minPlayers, int maxPlayers, Waiting waiting,
        List<Arena> arenas, Map<String, List<Feature>> features) {

    public Mode {
        arenas = List.copyOf(arenas);
        features = Map.copyOf(features);
    }
}
