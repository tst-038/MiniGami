package io.github.tst_038.minigami.paper.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.api.stat.StatTracker;
import io.github.tst_038.minigami.buildbattle.BuildBattle;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.team.RoleShare;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import io.github.tst_038.minigami.features.breakblocks.BreakBlocksFactory;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.diagnosis.Hosting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import io.github.tst_038.minigami.paper.hud.Hud;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GameLoaderTest {

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
    private final IdRegistry<StatGroup> statGroups = new IdRegistry<>(StatGroup::id);
    private final IdRegistry<Arena> arenas = new IdRegistry<>(Arena::id);
    private final YamlConfiguration hud = new YamlConfiguration();

    /** A game whose code relies on a seeker and a hider. */
    private static final GameType HIDE = new GameType() {
        @Override
        public String id() {
            return "hide";
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
        public List<String> roles() {
            return List.of("seeker", "hider");
        }
    };

    /** A game whose code relies on a hill region in its arenas. */
    private static final GameType HILL = new GameType() {
        @Override
        public String id() {
            return "hill";
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
        public List<String> regions() {
            return List.of("hill");
        }
    };

    GameLoaderTest() {
        games.register(GAME);
        games.register(HIDE);
        games.register(HILL);
        arenas.register(new Arena("hills", null, List.of(new Arena.Spawn(new Location(null, 0, 64, 0), Optional.empty())), Optional.empty(), Optional.empty(),
                Map.of("hill", new Region("hill", null, 0, 64, 0, 4, 64, 4), "plot-1", new Region("plot-1", null, 0, 64, 0, 4, 64, 4),
                        "plot-2", new Region("plot-2", null, 0, 64, 0, 4, 64, 4), "plot-3", new Region("plot-3", null, 0, 64, 0, 4, 64, 4)),
                Optional.of(new Region("map", null, 0, 64, 0, 4, 64, 4))));
        features.register(new VoidKillsFactory());
        features.register(new BreakBlocksFactory());
        arenas.register(new Arena("demo", null, List.of(new Arena.Spawn(new Location(null, 0, 64, 0), Optional.empty())), Optional.empty(), Optional.empty(), Map.of(), Optional.empty()));
        arenas.register(new Arena("empty", null, List.of(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty()));
    }

    private YamlFiles.Loaded<Game> load(String yaml) throws Exception {
        return load(yaml, true);
    }

    private YamlFiles.Loaded<Game> load(String yaml, boolean hasLobby) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new GameLoader(games, features, statGroups, Hosting.checks(true, () -> io.github.tst_038.minigami.api.loot.LootTables.NONE)).load(new YamlFiles("games", Map.of("fall", config), List.of()), arenas, hasLobby, hud, Set.of("match.joined", "match.left")::contains);
    }

    private YamlFiles.Loaded<Game> loadAll(Map<String, String> yamlById) throws Exception {
        var files = new HashMap<String, YamlConfiguration>();
        for (var entry : yamlById.entrySet()) {
            var config = new YamlConfiguration();
            config.loadFromString(entry.getValue());
            files.put(entry.getKey(), config);
        }
        return new GameLoader(games, features, statGroups, Hosting.checks(true, () -> io.github.tst_038.minigami.api.loot.LootTables.NONE)).load(new YamlFiles("games", files, List.of()), arenas, true, hud, Set.of("match.joined", "match.left")::contains);
    }

    private static List<String> problems(YamlFiles.Loaded<Game> loaded) {
        return loaded.problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void loadsAValidGame() throws Exception {
        var loaded = load("""
                gametype: test
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
        var variant = loaded.entries().find("fall").orElseThrow().base();
        assertEquals(GAME, variant.gametype());
        assertEquals("<gold>Void Fall", variant.name());
        assertEquals(2, variant.minPlayers());
        assertEquals(8, variant.maxPlayers());
        assertEquals(Waiting.IN_PLACE, variant.waiting());
        assertEquals(List.of("demo"), variant.arenas().stream().map(Arena::id).toList());
        assertEquals(1, variant.features().get("playing").size());
        assertEquals(0, variant.lives());
        assertEquals(Optional.empty(), variant.teams());
    }

    @Test
    void theHudOfConfigIsTheDefaultThatAGameOrVariantOverridesPartOf() throws Exception {
        hud.loadFromString("""
                sidebar: { waiting: ["%game%", "%players%"], default: ["%game%", "%time%"] }
                boss-bar: { color: yellow }
                titles: true
                sounds: { start: entity.player.levelup, win: false }
                """);
        var loaded = loadAll(Map.of("plain", """
                        gametype: test
                        min-players: 1
                        max-players: 2
                        waiting: arena
                        arenas: [demo]
                        variants:
                          own:
                            name: Own
                            hud:
                              sidebar: { playing: ["%team%", "", "%scores%", "<gray>discord.gg/x"] }
                              boss-bar: false
                              titles: { stay: 60 }
                          none: { name: None, hud: false }
                          quick: { name: Quick, hud: { titles: { fade-in: 5, stay: 20, fade-out: 5 } } }
                          quiet: { name: Quiet, hud: { titles: false } }
                        """,
                "broken", "{gametype: test, min-players: 1, max-players: 2, waiting: arena, arenas: [demo], variants: {odd: {name: Odd, hud: {sidebar: {fighting: [game], playing: [lives, \"%scores% left\"]}, sounds: {jump: x}, titles: {stay: -1}}}}}"));

        var plain = loaded.entries().find("plain").orElseThrow();
        assertEquals(List.of("%game%", "%time%"), plain.base().hud().sidebar("playing"));
        assertEquals(Optional.of(BossBar.Color.YELLOW), plain.base().hud().bossBar().map(Hud.Bar::color));
        assertEquals(Optional.of(Title.DEFAULT_TIMES), plain.base().hud().titles());
        assertEquals(Map.of(Hud.Moment.START, Key.key("entity.player.levelup")), plain.base().hud().sounds());
        var own = plain.variants().get("own").hud();
        assertEquals(List.of("%game%", "%players%"), own.sidebar("waiting"));
        assertEquals(List.of("%team%", "", "%scores%", "<gray>discord.gg/x"), own.sidebar("playing"));
        assertEquals(Optional.empty(), own.bossBar());
        assertEquals(Optional.of(Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))), own.titles());
        assertEquals(Hud.NONE, plain.variants().get("none").hud());
        assertEquals(Optional.of(Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(1), Duration.ofMillis(250))),
                plain.variants().get("quick").hud().titles());
        assertEquals(Optional.empty(), plain.variants().get("quiet").hud().titles());
        assertEquals(List.of("games/broken.yml: variants.odd.hud.sidebar.fighting: unknown phase; the game has: waiting, playing, or starting or default",
                "games/broken.yml: variants.odd.hud.sidebar.playing: 'lives': lines are texts now, such as \"<gray>Alive: <white>%alive%\"",
                "games/broken.yml: variants.odd.hud.sidebar.playing: %scores% must be a line of its own",
                "games/broken.yml: variants.odd.hud.titles.stay: must be at least 0",
                "games/broken.yml: variants.odd.hud.sounds.jump: unknown moment; moments: countdown, start, kill, out, win, podium, lose"), problems(loaded));
    }

    @Test
    void aGameHasItsOwnWordingThatVariantsOverlayAndTyposAreCaught() throws Exception {
        var loaded = loadAll(Map.of("plain", """
                        gametype: test
                        min-players: 1
                        max-players: 2
                        waiting: arena
                        arenas: [demo]
                        messages:
                          match: { joined: "in", left: "out" }
                        variants:
                          loud:
                            name: Loud
                            messages: { match: { left: "OUT" } }
                        """,
                "broken", "{gametype: test, min-players: 1, max-players: 2, waiting: arena, arenas: [demo], messages: {match: {joind: x}}}"));

        var plain = loaded.entries().find("plain").orElseThrow();
        assertEquals(Map.of("match.joined", "in", "match.left", "out"), plain.base().messages());
        assertEquals(Map.of("match.joined", "in", "match.left", "OUT"), plain.variants().get("loud").messages());
        assertEquals(List.of("games/broken.yml: messages.match.joind: unknown message"), problems(loaded));
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

        var byGame = load("{gametype: one-life, min-players: 1, max-players: 1, waiting: arena, arenas: [demo]}");
        var set = load("{gametype: one-life, min-players: 1, max-players: 1, waiting: arena, arenas: [demo], lives: 3}");

        assertEquals(1, byGame.entries().find("fall").orElseThrow().base().lives());
        assertEquals(3, set.entries().find("fall").orElseThrow().base().lives());
    }

    @Test
    void aGameReadsItsOwnSettingsAndOtherGamesDontKnowThem() throws Exception {
        games.register(new BuildBattle());

        var buildBattle = load("{gametype: build-battle, min-players: 1, max-players: 1, waiting: arena, arenas: [hills], build-seconds: 60}");
        var other = load("{gametype: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo], build-seconds: 60}");

        assertEquals(List.of(), problems(buildBattle));
        assertEquals(60 * 20, buildBattle.entries().find("fall").orElseThrow().base().gametype().phases().get(1).durationTicks());
        assertEquals(List.of("games/fall.yml: build-seconds: unknown key"), problems(other));
    }

    @Test
    void nameDefaultsToTheIdAndFeaturesAreOptional() throws Exception {
        var variant = load("{gametype: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo]}")
                .entries().find("fall").orElseThrow().base();

        assertEquals("fall", variant.name());
        assertTrue(variant.features().isEmpty());
    }

    @Test
    void quittingPutsPlayersOutUnlessTheGameGivesThemAWayBack() throws Exception {
        var plain = load("{gametype: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo]}");
        var back = load("{gametype: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo], quit: {then: die, rejoin-seconds: 60}}");
        var stuck = load("{gametype: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo], quit: {then: stay}}");

        assertEquals(Quit.OUT, plain.entries().find("fall").orElseThrow().base().quit());
        assertEquals(new Quit(Quit.Then.DIE, 60), back.entries().find("fall").orElseThrow().base().quit());
        assertEquals(List.of("games/fall.yml: quit.then: stay needs rejoin-seconds above 0; without a way back quitting puts them out"),
                problems(stuck));
    }

    @Test
    void reportsEveryProblemOfAnInvalidGame() throws Exception {
        var loaded = load("""
                gametype: spleef
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
                "games/fall.yml: gametype: unknown game type 'spleef'; known: hide, hill, test",
                "games/fall.yml: max-players: must be at least min-players (4)",
                "games/fall.yml: waiting: must be one of: in-place, lobby, arena",
                "games/fall.yml: arenas: no valid arena 'nope'; arenas: demo, empty, hills",
                "games/fall.yml: features.playing.void-kills.y: must be a number"), problems(loaded));
    }

    @Test
    void anArenaWithoutSpawnsIsLeftOutUntilItHasSome() throws Exception {
        var loaded = load("{gametype: test, min-players: 1, max-players: 1, waiting: arena, arenas: [demo, empty]}");

        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("demo"), loaded.entries().find("fall").orElseThrow().base().arenas().stream().map(Arena::id).toList());
    }

    @Test
    void featuresMustBeInAPhaseOfTheGame() throws Exception {
        var loaded = load("""
                gametype: test
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
                "games/fall.yml: features.ending: unknown phase; test has: waiting, playing",
                "games/fall.yml: features.playing.void-kils: unknown feature; known: break-blocks, void-kills"), problems(loaded));
    }

    @Test
    void blocksCanOnlyChangeInPhasesWhereTheGameLetsPlayersDoIt() throws Exception {
        games.register(new GameType() {
            @Override
            public String id() {
                return "dig";
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
            public GameMode gameMode(String phase) {
                return phase.equals("playing") ? GameMode.SURVIVAL : GameMode.ADVENTURE;
            }
        });
        var header = "min-players: 1\nmax-players: 2\nwaiting: arena\narenas: [demo]\nfeatures:\n  playing:\n    break-blocks: {}\n";

        assertEquals(List.of(), problems(load("gametype: dig\n" + header)));
        assertEquals(List.of("games/fall.yml: features.playing.break-blocks: changes blocks, but test keeps players in adventure mode in playing, where they can't"),
                problems(load("gametype: test\n" + header)));
    }

    @Test
    void aVariantKeepsTheStatsOfItsStatGroups() throws Exception {
        var goals = new Stat("goals");
        record Scoring(String id, List<StatTracker> trackers) implements StatGroup { }
        statGroups.register(new Scoring("scoring", List.of(new StatTracker() {
            @Override
            public Stat stat() {
                return goals;
            }

            @Override
            public void track(MatchContext context) { }
        })));

        var loaded = loadAll(Map.of(
                "plain", "{gametype: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], variants: {scored: {name: Scored, stats: [scoring]}}}",
                "typo", "{gametype: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], variants: {typo: {name: Typo, stats: [scorign]}}}"));

        assertEquals(List.of("games/typo.yml: variants.typo.stats: unknown stat group 'scorign'; known: scoring"), problems(loaded));
        var plain = loaded.entries().find("plain").orElseThrow();
        assertEquals(Set.of(), plain.base().stats());
        assertEquals(Set.of(goals), plain.variants().get("scored").stats());
    }

    @Test
    void aVariantIsLaidOverItsGameKeyByKeyAndCanTurnItsFeaturesOff() throws Exception {
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

        var game = load("""
                gametype: test
                min-players: 2
                max-players: 8
                waiting: arena
                arenas: [demo]
                features: { playing: { probe: { a: 1, b: 2 } } }
                variants:
                  changed: { name: Changed, min-players: 1, features: { playing: { probe: { b: 5 } } } }
                  calm: { name: Calm, features: { playing: { probe: false } } }
                """);

        assertEquals(List.of(), problems(game));
        assertEquals(List.of("1,2", "1,5"), read);
        var fall = game.entries().find("fall").orElseThrow();
        var changed = fall.variants().get("changed");
        assertEquals(1, changed.minPlayers());
        assertEquals(8, changed.maxPlayers());
        assertEquals("fall changed", changed.key());
        assertEquals("Changed", changed.name());
        assertTrue(fall.variants().get("calm").features().get("playing").isEmpty());
        assertEquals(Optional.of(changed), fall.variant(Optional.of("changed")));
        assertEquals(Optional.of(fall.base()), fall.variant(Optional.empty()));
    }

    @Test
    void aVariantNeedsANameAndTheGamesType() throws Exception {
        var loaded = load("""
                gametype: test
                min-players: 1
                max-players: 1
                waiting: arena
                arenas: [demo]
                variants:
                  nameless: { lives: 3 }
                  other: { name: Other, gametype: hide }
                  Bad: { name: Bad }
                  flat: 3
                """);

        assertTrue(loaded.entries().ids().isEmpty());
        assertEquals(List.of(
                "games/fall.yml: variants.Bad: use lower case letters, digits and dashes",
                "games/fall.yml: variants.flat: must be the settings that differ from the game, such as { name: ..., lives: 3 }",
                "games/fall.yml: variants.nameless: needs a name, so players can tell it from the game itself",
                "games/fall.yml: variants.other.gametype: a variant plays its game's game type; make another game for another type"),
                problems(loaded).stream().sorted().toList());
    }

    @Test
    void gamesDontExtendEachOther() throws Exception {
        var loaded = load("{gametype: test, extends: other, min-players: 1, max-players: 1, waiting: arena, arenas: [demo]}");

        assertEquals(List.of("games/fall.yml: extends: games don't extend each other; put what differs under variants, or copy the other game's file"),
                problems(loaded));
    }

    @Test
    void aVariantRepeatsNoProblemOfItsGame() throws Exception {
        var loaded = load("{gametype: test, min-players: 1, max-players: 1, waiting: arena, arenas: [nope], variants: {more: {name: More, lives: 3}}}");

        assertEquals(1, problems(loaded).size());
        assertTrue(problems(loaded).getFirst().startsWith("games/fall.yml: arenas"));
    }

    @Test
    void waitingInTheLobbyNeedsALobby() throws Exception {
        var yaml = "{gametype: test, min-players: 1, max-players: 1, waiting: lobby, arenas: [demo]}";

        assertEquals(List.of(), problems(load(yaml, true)));
        assertEquals(List.of("games/fall.yml: waiting: no lobby is set; set one with /mg lobby set or in config.yml"),
                problems(load(yaml, false)));
    }

    @Test
    void teamsAreAFixedNumberOrAFixedSize() throws Exception {
        var byCount = load("{gametype: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], teams: {count: 2}}");
        var bySize = load("{gametype: test, min-players: 3, max-players: 8, waiting: arena, arenas: [demo], teams: {size: 2}}");

        assertEquals(List.of(), problems(byCount));
        assertEquals(List.of(), problems(bySize));
        var count = byCount.entries().find("fall").orElseThrow().base().teams().orElseThrow();
        var size = bySize.entries().find("fall").orElseThrow().base().teams().orElseThrow();
        assertEquals(2, count.teamsFor(7));
        assertEquals(4, size.teamsFor(7));
        assertEquals(1, size.teamsFor(1));
    }

    @Test
    void reportsTeamsThatCanNotWork() throws Exception {
        var variant = "gametype: test, waiting: arena, arenas: [demo], ";

        assertEquals(List.of("games/fall.yml: teams: set either count or size"),
                problems(load("{" + variant + "min-players: 2, max-players: 8, teams: {count: 2, size: 2}}")));
        assertEquals(List.of("games/fall.yml: min-players: must be at least the number of teams (3)"),
                problems(load("{" + variant + "min-players: 2, max-players: 8, teams: {count: 3}}")));
        assertEquals(List.of("games/fall.yml: teams.count: must be between 2 and 16"),
                problems(load("{" + variant + "min-players: 2, max-players: 8, teams: {count: 17}}")));
        assertEquals(List.of("games/fall.yml: teams.size: teams of one are free for all; leave out teams instead"),
                problems(load("{" + variant + "min-players: 2, max-players: 8, teams: {size: 1}}")));
        assertEquals(List.of("games/fall.yml: teams.size: max-players makes 20 teams; at most 16 are possible"),
                problems(load("{" + variant + "min-players: 3, max-players: 40, teams: {size: 2}}")));
        assertEquals(List.of("games/fall.yml: min-players: must be more than the team size (2), so there are at least two teams"),
                problems(load("{" + variant + "min-players: 2, max-players: 8, teams: {size: 2}}")));
    }

    @Test
    void rolesMayPlayAgainstSoloRunnersOrASingleTeam() throws Exception {
        var game = "{gametype: test, waiting: arena, arenas: [demo], roles: {hunter: {per: 5}}, ";

        assertEquals(List.of(), problems(load(game + "min-players: 2, max-players: 10, teams: {size: 1}}")));
        assertEquals(List.of(), problems(load(game + "min-players: 2, max-players: 10, teams: {size: 2}}")));
        assertEquals(List.of("games/fall.yml: teams.size: max-players leaves 18 players for teams, which makes 18 teams; at most 16 are possible"),
                problems(load(game + "min-players: 2, max-players: 22, teams: {size: 1}}")));
    }

    @Test
    void theRolesPlayersDoNotCountTowardsTheTeamLimit() throws Exception {
        var game = "{gametype: test, min-players: 5, waiting: arena, arenas: [demo], roles: {seeker: {per: 5}}, teams: {size: 3}, ";

        assertEquals(List.of(), problems(load(game + "max-players: 27}")));
        assertEquals(List.of("games/fall.yml: teams.size: max-players leaves 60 players for teams, which makes 20 teams; at most 16 are possible"),
                problems(load(game + "max-players: 75}")));
    }

    @Test
    void rolesAreHandedOutInFileOrderWithTheirShareColourAndLives() throws Exception {
        var loaded = load("{gametype: test, min-players: 3, max-players: 8, waiting: arena, arenas: [demo], roles: {"
                + "seeker: {name: Seekers, color: dark_red, players: 1, lives: 0}, hider: {}, guard: {per: 4}}}");

        assertEquals(List.of(), problems(loaded));
        var variant = loaded.entries().find("fall").orElseThrow().base();
        assertEquals(List.of(
                new Role("seeker", "Seekers", NamedTextColor.DARK_RED, RoleShare.of(1), OptionalInt.of(0), Optional.empty(), Optional.empty(), Map.of()),
                new Role("hider", "hider", NamedTextColor.BLUE, RoleShare.rest(), OptionalInt.empty(), Optional.empty(), Optional.empty(), Map.of()),
                new Role("guard", "guard", NamedTextColor.GREEN, RoleShare.onePer(4), OptionalInt.empty(), Optional.empty(), Optional.empty(), Map.of())), variant.roles());
        assertEquals(Optional.of("Seekers"), variant.role("seeker").map(Role::name));
    }

    @Test
    void rolesTakeTheirShareAndTeamsSplitThePlayersLeft() throws Exception {
        var loaded = load("{gametype: test, min-players: 4, max-players: 12, waiting: arena, arenas: [demo], "
                + "roles: {minotaur: {per: 5}}, teams: {count: 2}}");

        assertEquals(List.of(), problems(loaded));
        var variant = loaded.entries().find("fall").orElseThrow().base();
        assertEquals(List.of("minotaur"), variant.roles().stream().map(Role::id).toList());
        assertEquals(Optional.of(2), variant.teams().map(TeamSetup::count));
    }

    @Test
    void anArenaNeedsARegionForEveryTeamAMatchCanHaveToBeUsed() throws Exception {
        games.register(new BuildBattle());
        var variant = "{gametype: build-battle, min-players: 1, waiting: arena, arenas: [hills], ";

        assertEquals(List.of("hills"), arenaIds(load(variant + "max-players: 3}")));
        assertEquals(List.of("hills"), arenaIds(load(variant.replace("min-players: 1", "min-players: 3") + "max-players: 6, teams: {size: 2}}")));
        assertEquals(List.of(), arenaIds(load(variant + "max-players: 4}")));
    }

    @Test
    void aGameThatChangesBlocksOnlyPlaysInArenasWithASavedMap() throws Exception {
        games.register(new BuildBattle());
        var hills = arenas.find("hills").orElseThrow();
        arenas.register(new Arena("unmapped", null, hills.spawns(), Optional.empty(), Optional.empty(), hills.regions(), Optional.empty()));

        var loaded = load("{gametype: build-battle, min-players: 1, max-players: 3, waiting: arena, arenas: [hills, unmapped]}");

        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("hills"), arenaIds(loaded));
    }

    @Test
    void anArenaWithoutTheRegionsTheGameNeedsIsLeftOut() throws Exception {
        var variant = "{gametype: hill, min-players: 2, max-players: 8, waiting: arena, arenas: ";

        var loaded = load(variant + "[hills, demo, empty]}");

        // An arena lacking them is still being set up: left out, but no problem, so saving its regions can reload.
        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("hills"), arenaIds(loaded));
        // The findings say why the others are left out.
        assertEquals(List.of("demo missing-region", "empty no-spawns", "empty missing-region"), loaded.entries().find("fall").orElseThrow().base().findings().stream()
                .map(finding -> finding.arena() + " " + finding.code()).toList());
    }

    private static List<String> arenaIds(YamlFiles.Loaded<Game> loaded) {
        return loaded.entries().find("fall").orElseThrow().base().arenas().stream().map(Arena::id).toList();
    }

    @Test
    void aRoleCanSwitchItsMembersToAnotherRoleInsteadOfTheirGoingOut() throws Exception {
        var variant = "{gametype: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], roles: {seeker: {players: 1}, ";

        var loaded = load(variant + "hider: {on-out: seeker}}}");
        assertEquals(List.of(), problems(loaded));
        assertEquals(Optional.of("seeker"), loaded.entries().find("fall").orElseThrow().base().role("hider").orElseThrow().onOut());
        assertEquals(List.of("games/fall.yml: roles.hider.on-out: must be one of the other roles: seeker"), problems(load(variant + "hider: {on-out: zombie}}}")));
        assertEquals(List.of("games/fall.yml: roles.hider.on-out: must be one of the other roles: seeker"), problems(load(variant + "hider: {on-out: hider}}}")));
    }

    @Test
    void aRoleHasItsOwnFeaturesInPhasesOfTheGame() throws Exception {
        var loaded = load("{gametype: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], roles: {"
                + "seeker: {players: 1, features: {playing: {void-kills: {y: 0}}}}, hider: {features: {fighting: {void-kills: {}}}}}}");

        assertEquals(List.of("games/fall.yml: roles.hider.features.fighting: unknown phase; test has: waiting, playing"), problems(loaded));
        var good = load("{gametype: test, min-players: 2, max-players: 8, waiting: arena, arenas: [demo], roles: {"
                + "seeker: {players: 1, features: {playing: {void-kills: {y: 0}}}}, hider: {}}}");
        var seeker = good.entries().find("fall").orElseThrow().base().role("seeker").orElseThrow();
        assertEquals(1, seeker.features().get("playing").size());
        assertEquals(Map.of(), good.entries().find("fall").orElseThrow().base().features());
    }

    @Test
    void aDisabledGameIsLeftOut() throws Exception {
        var loaded = loadAll(Map.of(
                "off", "enabled: false\ngametype: nonsense",
                "on", "enabled: true\ngametype: nonsense"));
        // The disabled file isn't even read; the enabled one is checked like any other.
        assertFalse(problems(loaded).isEmpty());
        assertTrue(problems(loaded).stream().allMatch(problem -> problem.contains("on.yml")));
        assertTrue(loaded.entries().ids().isEmpty());
    }

    @Test
    void reportsRolesThatCanNotWork() throws Exception {
        var variant = "gametype: test, waiting: arena, arenas: [demo], max-players: 8, ";

        assertEquals(List.of("games/fall.yml: roles: with teams, the players left after the roles form the teams; give players or per to b"),
                problems(load("{" + variant + "min-players: 4, teams: {count: 2}, roles: {a: {players: 1}, b: {}}}")));
        assertEquals(List.of("games/fall.yml: min-players: must be at least 5: the roles ask for 3 players and the teams need 2 more"),
                problems(load("{" + variant + "min-players: 4, teams: {count: 2}, roles: {a: {players: 1}, b: {per: 2}}}")));
        assertEquals(List.of("games/fall.yml: min-players: must be at least 4: the roles ask for 3 players and the teams need 1 more"),
                problems(load("{" + variant + "min-players: 3, teams: {size: 3}, roles: {a: {players: 3}}}")));
        assertEquals(List.of("games/fall.yml: roles: needs at least two roles"),
                problems(load("{" + variant + "min-players: 2, roles: {a: {}}}")));
        assertEquals(List.of("games/fall.yml: roles: exactly one role must leave out players and per, to get everyone left over; a, b do"),
                problems(load("{" + variant + "min-players: 2, roles: {a: {}, b: {}}}")));
        assertEquals(List.of("games/fall.yml: roles: exactly one role must leave out players and per, to get everyone left over; none does"),
                problems(load("{" + variant + "min-players: 2, roles: {a: {players: 1}, b: {per: 2}}}")));
        assertEquals(List.of("games/fall.yml: min-players: must be more than the 2 players the other roles ask for, so c gets at least one"),
                problems(load("{" + variant + "min-players: 2, roles: {a: {players: 1}, b: {per: 5}, c: {}}}")));
        assertEquals(List.of("games/fall.yml: roles.a.per: set either players or per"),
                problems(load("{" + variant + "min-players: 3, roles: {a: {players: 1, per: 2}, b: {}}}")));
        assertEquals(List.of("games/fall.yml: roles.a.color: unknown colour 'pink'; colours: " + String.join(", ", NamedTextColor.NAMES.keys())),
                problems(load("{" + variant + "min-players: 2, roles: {a: {players: 1, color: pink}, b: {}}}")));
        assertEquals(List.of("games/fall.yml: roles.Big_One: use lower case letters, digits and dashes"),
                problems(load("{" + variant + "min-players: 2, roles: {Big_One: {players: 1}, b: {}}}")));
    }

    @Test
    void aGameDefinesExactlyTheRolesItsGameNeeds() throws Exception {
        var variant = "gametype: hide, waiting: arena, arenas: [demo], min-players: 2, max-players: 8, ";

        assertEquals(List.of(), problems(load("{" + variant + "roles: {hider: {}, seeker: {players: 1}}}")));
        assertEquals(List.of("games/fall.yml: roles: hide needs the roles seeker, hider"), problems(load("{" + variant + "}")));
        assertEquals(List.of("games/fall.yml: roles: hide needs exactly the roles seeker, hider; missing: hider; unknown: runner"),
                problems(load("{" + variant + "roles: {seeker: {players: 1}, runner: {}}}")));
    }
}
