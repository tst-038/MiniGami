package io.github.tst_038.minigami.paper.mode;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns {@code modes/<id>.yml} files into modes:
 *
 * <pre>
 * game: test
 * name: "&lt;gold&gt;Void Fall"   # optional, defaults to the id
 * min-players: 2
 * max-players: 8
 * waiting: arena              # in-place, lobby or arena
 * arenas: [demo]
 * features:                   # optional; per phase of the game, per feature
 *   playing:
 *     void-kills: { y: 0 }
 * </pre>
 */
public final class ModeLoader {

    private final IdRegistry<GameType> games;
    private final IdRegistry<FeatureFactory> features;

    public ModeLoader(IdRegistry<GameType> games, IdRegistry<FeatureFactory> features) {
        this.games = games;
        this.features = features;
    }

    /** @param arenas the arenas loaded so far; a mode may only use playable ones */
    public YamlFiles.Loaded<Mode> load(YamlFiles files, IdRegistry<Arena> arenas) {
        return files.parse(Mode::id, (id, config) -> parse(id, config, arenas));
    }

    private Mode parse(String id, ConfigReader config, IdRegistry<Arena> arenas) {
        var gameId = config.string("game");
        var game = games.find(gameId).orElse(null);
        if (!gameId.isEmpty() && game == null) {
            config.problem("game", "unknown game '" + gameId + "'; known: " + String.join(", ", games.ids()));
        }
        var name = config.has("name") ? config.string("name") : id;
        var minPlayers = config.integer("min-players", 1, Integer.MAX_VALUE);
        var maxPlayers = config.integer("max-players", 1, Integer.MAX_VALUE);
        if (maxPlayers < minPlayers) {
            config.problem("max-players", "must be at least min-players (" + minPlayers + ")");
        }
        var waiting = config.choice("waiting", Waiting.class);
        return new Mode(id, game, name, minPlayers, maxPlayers, waiting, arenas(config, arenas),
                config.has("features") ? features(config.section("features"), game) : Map.of());
    }

    private static List<Arena> arenas(ConfigReader config, IdRegistry<Arena> registry) {
        var ids = config.strings("arenas");
        if (ids.isEmpty() && config.has("arenas")) {
            config.problem("arenas", "needs at least one arena");
        }
        var arenas = new ArrayList<Arena>();
        for (String id : ids) {
            var arena = registry.find(id);
            if (arena.isEmpty()) {
                config.problem("arenas", "no valid arena '" + id + "'; arenas: " + String.join(", ", registry.ids()));
            } else if (!arena.get().isPlayable()) {
                config.problem("arenas", "arena '" + id + "' has no spawns yet");
            } else {
                arenas.add(arena.get());
            }
        }
        return arenas;
    }

    private Map<String, List<Feature>> features(ConfigReader config, GameType game) {
        var phases = game == null ? null : game.phases().stream().map(Phase::id).toList();
        var byPhase = new HashMap<String, List<Feature>>();
        for (String phase : config.keys()) {
            if (phases != null && !phases.contains(phase)) {
                config.problem(phase, "unknown phase; " + game.id() + " has: " + String.join(", ", phases));
                continue;
            }
            var phaseConfig = config.section(phase);
            var phaseFeatures = new ArrayList<Feature>();
            for (String featureId : phaseConfig.keys()) {
                var factory = features.find(featureId);
                if (factory.isEmpty()) {
                    phaseConfig.problem(featureId, "unknown feature; known: " + String.join(", ", features.ids()));
                } else {
                    phaseFeatures.add(factory.get().create(phaseConfig.section(featureId)));
                }
            }
            byPhase.put(phase, List.copyOf(phaseFeatures));
        }
        return byPhase;
    }
}
