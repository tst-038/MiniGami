package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.hud.Hud;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What is played: a game as set up in {@code games/<game>.yml}, or one of its variants laid over
 * it. Its player limits, where players wait, the arenas it can be played in, and the configured
 * features per phase.
 *
 * @param game the id of the game it belongs to
 * @param id the variant's id; empty for the game itself
 * @param name display name, in MiniMessage
 * @param lives how many deaths put a player out; 0 means they always respawn
 * @param teams empty when everyone plays on their own or it has roles
 * @param roles in the order they are handed out; empty when it has none
 * @param quit what quitting a running match does to a player still in it
 * @param features feature instances are shared by every match of it, so they keep no match state themselves
 * @param statGroups the stat groups it keeps, next to the stats of the game type itself
 * @param hud what players see of a match next to chat
 */
public record Variant(String game, Optional<String> id, GameType gametype, String name, int minPlayers, int maxPlayers, Waiting waiting, int lives,
        Optional<TeamSetup> teams, List<Role> roles, Quit quit, List<Arena> arenas, Map<String, List<Feature>> features, List<StatGroup> statGroups, Hud hud) {

    public Variant {
        id.ifPresent(variant -> {
            if (variant.isEmpty()) {
                throw new IllegalArgumentException("A variant's id can't be empty");
            }
        });
        roles = List.copyOf(roles);
        arenas = List.copyOf(arenas);
        features = Map.copyOf(features);
        statGroups = List.copyOf(statGroups);
    }

    static Stream<Feature> allFeatures(GameType gametype, Map<String, List<Feature>> features, List<Role> roles) {
        return Stream.concat(Stream.of(features, gametype == null ? Map.<String, List<Feature>>of() : gametype.features()), roles.stream().map(Role::features))
                .flatMap(byPhase -> byPhase.values().stream()).flatMap(List::stream);
    }

    /** For logs and admins: the game's id, then the variant's if it is one, such as {@code spleef duos}. */
    public String key() {
        return id.map(variant -> game + " " + variant).orElse(game);
    }

    /** The stats a match of it keeps: the game type's own and those of its stat groups. */
    public Set<Stat> stats() {
        return Stream.concat(gametype.stats().stream(), statGroups.stream().flatMap(group -> group.stats().stream()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<Role> role(String id) {
        return roles.stream().filter(role -> role.id().equals(id)).findFirst();
    }
}
