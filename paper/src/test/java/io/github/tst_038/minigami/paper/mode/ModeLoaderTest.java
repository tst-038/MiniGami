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
import io.github.tst_038.minigami.buildbattle.BuildBattle;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.ArrayList;
import java.util.HashMap;
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
        arenas.register(new Arena("demo", null, List.of(new Arena.Spawn(new Location(null, 0, 64, 0), Optional.empty())), Optional.empty(), Map.of(), Optional.empty()));
        arenas.register(new Arena("empty", null, List.of(), Optional.empty(), Map.of(), Optional.empty()));
    }

    private YamlFiles.Loaded<Mode> load(String yaml) throws Exception {
        return load(yaml, true);
    }

    private YamlFiles.Loaded<Mode> load(String yaml, boolean hasLobby) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new ModeLoader(games, features).load(new YamlFiles("modes", Map.of("fall", config), List.of()), arenas, hasLobby);
    }

    private YamlFiles.Loaded<Mode> loadAll(Map<String, String> yamlById) throws Exception {
        var files = new HashMap<String, YamlConfiguration>();
        for (var entry : yamlById.entrySet()) {
            var config = new YamlConfiguration();
            config.loadFromString(entry.getValue());
            files.put(entry.getKey(), config);
        }
        return new ModeLoader(games, features).load(new YamlFiles("modes", files, List.of()), arenas, true);
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
        assertEquals(Optional.empty(), mode.teams());
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
    void aGameReadsItsOwnSettingsAndOtherGamesDontKnowThem() throws Exception {
        games.register(new BuildBattle());

        var buildBattle = load("{game: build-battle, min-players: 1, max-players: 1, waiting: arena, arenas: [demo], build-seconds: 60}");
        var other = load("{game: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo], build-seconds: 60}");

        assertEquals(List.of(), problems(buildBattle));
        assertEquals(60 * 20, buildBattle.entries().find("fall").orElseThrow().game().phases().get(1).durationTicks());
        assertEquals(List.of("modes/fall.yml: build-seconds: unknown key"), problems(other));
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
                "modes/fall.yml: features.playing.void-kills.y: must be a number"), problems(loaded));
    }

    @Test
    void anArenaWithoutSpawnsIsLeftOutUntilItHasSome() throws Exception {
        var loaded = load("{game: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo, empty]}");

        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("demo"), loaded.entries().find("fall").orElseThrow().arenas().stream().map(Arena::id).toList());
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
    void aModeExtendsAnotherKeyByKeyAndCanTurnItsFeaturesOff() throws Exception {
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

        var loaded = loadAll(Map.of(
                "base", "{game: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], features: {playing: {probe: {a: 1, b: 2}}}}",
                "changed", "{extends: base, min-players: 1, features: {playing: {probe: {b: 5}}}}",
                "off", "{extends: changed, features: {playing: {probe: false}}}"));

        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("1,2", "1,5"), read);
        var changed = loaded.entries().find("changed").orElseThrow();
        assertEquals(1, changed.minPlayers());
        assertEquals(8, changed.maxPlayers());
        assertTrue(loaded.entries().find("off").orElseThrow().features().get("playing").isEmpty());
    }

    @Test
    void inheritedSettingsAreTheModesOwnOverItsParents() throws Exception {
        var base = new YamlConfiguration();
        base.loadFromString("{game: test, max-players: 8, features: {playing: {void-kills: {y: 0}}}}");
        var child = new YamlConfiguration();
        child.loadFromString("{extends: base, max-players: 4}");

        var inherited = ModeLoader.inherited("child", Map.of("base", base, "child", child));

        assertEquals("test", inherited.getString("game"));
        assertEquals(4, inherited.getInt("max-players"));
        assertEquals(0, inherited.getInt("features.playing.void-kills.y"));
    }

    @Test
    void extendingAMissingModeOrInACircleIsAProblem() throws Exception {
        var mode = "min-players: 1, max-players: 1, waiting: arena, arenas: [demo]";

        var loaded = loadAll(Map.of(
                "lost", "{game: test, extends: nope, " + mode + "}",
                "a", "{game: test, extends: b, " + mode + "}",
                "b", "{game: test, extends: a, " + mode + "}"));

        assertTrue(loaded.entries().ids().isEmpty());
        assertEquals(List.of(
                "modes/a.yml: extends: goes round in a circle: a > b > a",
                "modes/b.yml: extends: goes round in a circle: b > a > b",
                "modes/lost.yml: extends: no mode 'nope'"), problems(loaded).stream().sorted().toList());
    }

    @Test
    void waitingInTheLobbyNeedsALobby() throws Exception {
        var yaml = "{game: test, min-players: 1, max-players: 1, waiting: lobby, arenas: [demo]}";

        assertEquals(List.of(), problems(load(yaml, true)));
        assertEquals(List.of("modes/fall.yml: waiting: no lobby is set; set one with /mg lobby set or in config.yml"),
                problems(load(yaml, false)));
    }

    @Test
    void teamsAreAFixedNumberOrAFixedSize() throws Exception {
        var byCount = load("{game: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], teams: {count: 2}}");
        var bySize = load("{game: test, min-players: 3, max-players: 8, waiting: arena, arenas: [demo], teams: {size: 2}}");

        assertEquals(List.of(), problems(byCount));
        assertEquals(List.of(), problems(bySize));
        var count = byCount.entries().find("fall").orElseThrow().teams().orElseThrow();
        var size = bySize.entries().find("fall").orElseThrow().teams().orElseThrow();
        assertEquals(2, count.teamsFor(7));
        assertEquals(4, size.teamsFor(7));
        assertEquals(1, size.teamsFor(1));
    }

    @Test
    void reportsTeamsThatCanNotWork() throws Exception {
        var mode = "game: test, waiting: arena, arenas: [demo], ";

        assertEquals(List.of("modes/fall.yml: teams: set either count or size"),
                problems(load("{" + mode + "min-players: 2, max-players: 8, teams: {count: 2, size: 2}}")));
        assertEquals(List.of("modes/fall.yml: min-players: must be at least the number of teams (3)"),
                problems(load("{" + mode + "min-players: 2, max-players: 8, teams: {count: 3}}")));
        assertEquals(List.of("modes/fall.yml: teams.count: must be between 2 and 8"),
                problems(load("{" + mode + "min-players: 2, max-players: 8, teams: {count: 9}}")));
        assertEquals(List.of("modes/fall.yml: teams.size: teams of one are free for all; leave out teams instead"),
                problems(load("{" + mode + "min-players: 2, max-players: 8, teams: {size: 1}}")));
        assertEquals(List.of("modes/fall.yml: teams.size: max-players makes 10 teams; at most 8 are possible"),
                problems(load("{" + mode + "min-players: 3, max-players: 20, teams: {size: 2}}")));
        assertEquals(List.of("modes/fall.yml: min-players: must be more than the team size (2), so there are at least two teams"),
                problems(load("{" + mode + "min-players: 2, max-players: 8, teams: {size: 2}}")));
    }
}
