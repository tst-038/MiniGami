package io.github.tst_038.minigami.paper.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ModeLoaderTest {

    private static final GameType GAME = new GameType() {
        @Override
        public String id() {
            return "test";
        }

        @Override
        public List<Phase> phases() {
            return List.of(Phase.untimed("waiting"), Phase.untimed("playing"));
        }

        @Override
        public Map<String, List<Feature>> features() {
            return Map.of();
        }

        @Override
        public WinCondition winCondition() {
            return new LastManStanding();
        }
    };

    private final IdRegistry<GameType> games = new IdRegistry<>(GameType::id);
    private final IdRegistry<FeatureFactory> features = new IdRegistry<>(FeatureFactory::id);
    private final IdRegistry<Arena> arenas = new IdRegistry<>(Arena::id);

    ModeLoaderTest() {
        games.register(GAME);
        features.register(new VoidKillsFactory());
        arenas.register(new Arena("demo", null, List.of(new Location(null, 0, 64, 0)), Optional.empty()));
        arenas.register(new Arena("empty", null, List.of(), Optional.empty()));
    }

    private YamlFiles.Loaded<Mode> load(String yaml) throws Exception {
        return load(yaml, true);
    }

    private YamlFiles.Loaded<Mode> load(String yaml, boolean hasLobby) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new ModeLoader(games, features).load(new YamlFiles("modes", Map.of("fall", config), List.of()), arenas, hasLobby);
    }

    private static List<String> problems(YamlFiles.Loaded<Mode> loaded) {
        return loaded.problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void loadsAValidMode() throws Exception {
        var loaded = load("""
                game: test
                name: "<gold>Void Fall"
                min-players: 2
                max-players: 8
                waiting: in-place
                arenas: [demo]
                features:
                  playing:
                    void-kills: { y: 0 }
                """);

        assertEquals(List.of(), problems(loaded));
        var mode = loaded.entries().find("fall").orElseThrow();
        assertEquals(GAME, mode.game());
        assertEquals("<gold>Void Fall", mode.name());
        assertEquals(2, mode.minPlayers());
        assertEquals(8, mode.maxPlayers());
        assertEquals(Waiting.IN_PLACE, mode.waiting());
        assertEquals(List.of("demo"), mode.arenas().stream().map(Arena::id).toList());
        assertEquals(1, mode.features().get("playing").size());
        assertEquals(0, mode.lives());
    }

    @Test
    void livesDefaultToTheGamesAndCanBeSet() throws Exception {
        games.register(new GameType() {
            @Override
            public String id() {
                return "one-life";
            }

            @Override
            public List<Phase> phases() {
                return GAME.phases();
            }

            @Override
            public Map<String, List<Feature>> features() {
                return Map.of();
            }

            @Override
            public WinCondition winCondition() {
                return new LastManStanding();
            }

            @Override
            public int lives() {
                return 1;
            }
        });

        var byGame = load("{game: one-life, min-players: 1, max-players: 1, waiting: arena, arenas: [demo]}");
        var set = load("{game: one-life, min-players: 1, max-players: 1, waiting: arena, arenas: [demo], lives: 3}");

        assertEquals(1, byGame.entries().find("fall").orElseThrow().lives());
        assertEquals(3, set.entries().find("fall").orElseThrow().lives());
    }

    @Test
    void nameDefaultsToTheIdAndFeaturesAreOptional() throws Exception {
        var mode = load("{game: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo]}")
                .entries().find("fall").orElseThrow();

        assertEquals("fall", mode.name());
        assertTrue(mode.features().isEmpty());
    }

    @Test
    void reportsEveryProblemOfAnInvalidMode() throws Exception {
        var loaded = load("""
                game: spleef
                min-players: 4
                max-players: 2
                waiting: anywhere
                arenas: [demo, nope, empty]
                features:
                  playing:
                    void-kills: { y: low }
                """);

        assertTrue(loaded.entries().ids().isEmpty());
        assertEquals(List.of(
                "modes/fall.yml: game: unknown game 'spleef'; known: test",
                "modes/fall.yml: max-players: must be at least min-players (4)",
                "modes/fall.yml: waiting: must be one of: in-place, lobby, arena",
                "modes/fall.yml: arenas: no valid arena 'nope'; arenas: demo, empty",
                "modes/fall.yml: arenas: arena 'empty' has no spawns yet",
                "modes/fall.yml: features.playing.void-kills.y: must be a number"), problems(loaded));
    }

    @Test
    void featuresMustBeInAPhaseOfTheGame() throws Exception {
        var loaded = load("""
                game: test
                min-players: 1
                max-players: 2
                waiting: arena
                arenas: [demo]
                features:
                  ending:
                    void-kills: { y: 0 }
                  playing:
                    void-kils: { y: 0 }
                """);

        assertEquals(List.of(
                "modes/fall.yml: features.ending: unknown phase; test has: waiting, playing",
                "modes/fall.yml: features.playing.void-kils: unknown feature; known: void-kills"), problems(loaded));
    }

    @Test
    void modeSettingsReplaceTheGamesDefaultsPerKeyAndCanTurnAFeatureOff() throws Exception {
        var read = new ArrayList<String>();
        features.register(new FeatureFactory() {
            @Override
            public String id() {
                return "probe";
            }

            @Override
            public Feature create(ConfigReader config) {
                read.add(config.integer("a", 0, 9) + "," + config.integer("b", 0, 9));
                return context -> { };
            }
        });
        games.register(new GameType() {
            @Override
            public String id() {
                return "defaults";
            }

            @Override
            public List<Phase> phases() {
                return GAME.phases();
            }

            @Override
            public Map<String, List<Feature>> features() {
                return Map.of();
            }

            @Override
            public Map<String, ?> defaultFeatures() {
                return Map.of("playing", Map.of("probe", Map.of("a", 1, "b", 2)));
            }

            @Override
            public WinCondition winCondition() {
                return new LastManStanding();
            }
        });
        var base = "{game: defaults, min-players: 1, max-players: 1, waiting: arena, arenas: [demo]";

        var byGame = load(base + "}");
        var changed = load(base + ", features: {playing: {probe: {b: 5}}}}");
        var off = load(base + ", features: {playing: {probe: false}}}");

        assertEquals(List.of(), problems(byGame));
        assertEquals(List.of(), problems(changed));
        assertEquals(List.of(), problems(off));
        assertEquals(List.of("1,2", "1,5"), read);
        assertEquals(1, byGame.entries().find("fall").orElseThrow().features().get("playing").size());
        assertTrue(off.entries().find("fall").orElseThrow().features().get("playing").isEmpty());
    }

    @Test
    void waitingInTheLobbyNeedsALobby() throws Exception {
        var yaml = "{game: test, min-players: 1, max-players: 1, waiting: lobby, arenas: [demo]}";

        assertEquals(List.of(), problems(load(yaml, true)));
        assertEquals(List.of("modes/fall.yml: waiting: no lobby is set; set one with /mg setlobby or in config.yml"),
                problems(load(yaml, false)));
    }
}
