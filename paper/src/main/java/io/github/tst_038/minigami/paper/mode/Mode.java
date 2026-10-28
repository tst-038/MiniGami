package io.github.tst_038.minigami.paper.mode;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A playable variant of a game, as set up in {@code modes/<id>.yml}: its player limits, where
 * players wait, the arenas it can be played in, and the configured features per phase.
 *
 * @param name display name, in MiniMessage
 * @param lives how many deaths put a player out; 0 means they always respawn
 * @param teams empty when everyone plays on their own or the mode has roles
 * @param roles in the order they are handed out; empty when the mode has none
 * @param quit what quitting a running match does to a player still in it
 * @param features feature instances are shared by every match of the mode, so they keep no match state themselves
 */
public record Mode(String id, GameType game, String name, int minPlayers, int maxPlayers, Waiting waiting, int lives,
        Optional<TeamSetup> teams, List<Role> roles, Quit quit, List<Arena> arenas, Map<String, List<Feature>> features) {

    public Mode {
        roles = List.copyOf(roles);
        arenas = List.copyOf(arenas);
        features = Map.copyOf(features);
    }

    public Optional<Role> role(String id) {
        return roles.stream().filter(role -> role.id().equals(id)).findFirst();
    }
}
