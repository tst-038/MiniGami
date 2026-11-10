package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import io.github.tst_038.minigami.core.diagnosis.Finding;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.diagnosis.Hosting;
import io.github.tst_038.minigami.paper.hud.Hud;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Turns {@code games/<id>.yml} files into games:
 *
 * <pre>
 * gametype: last-standing
 * name: "&lt;gold&gt;Void Fall"   # optional, defaults to the id
 * min-players: 2
 * max-players: 8
 * waiting: arena              # in-place, lobby or arena
 * teams: { count: 2 }         # optional; or { size: 2 }, teams of two
 * spawns:                   # optional; where players start and come back
 *   mode: random             # arena: the arena's spawns, which it must have; random: random places of the highest floor of the region or map, whatever spawns the arena has; left out: the arena's, or random if it has none and the game type allows
 *   teams: spread            # together (the default): a team shares one spawn, or spread: every player takes the next one
 *   respawn: highest-floor   # spawns (the default), or the highest floor still standing in region, for games that break their floor
 * respawn-seconds: 5         # optional; how long a player who died waits to come back, at the arena's respawning spot; 0 (the default): at once
 * name-tags: teammates       # optional; who sees a player's name tag: everyone (the default), teammates or nobody
 * messages:                  # optional; the game's own wording for any message key, over messages/en.yml
 *   hide-and-seek:
 *     hiding: "%prefix%<green>Run!"
 * roles:                      # optional; handed out in this order. Beside teams the roles go first, the teams split the rest (no role may then be the rest role)
 *   seeker: { name: "Seekers", color: red, players: 1, lives: 0 }   # or per: 5, one per five players
 *   hider:                                                         # neither: everyone left over
 *     name: "Hiders"
 *     on-out: seeker                                              # optional; joins it instead of going out
 *     name-tags: teammates                                        # optional; the role's own, over the game's
 *     features: { playing: { kit: { items: [stick] } } }          # optional; only for this role
 * ranks:                      # optional; split each team at random, in this order (needs teams or roles); one rank leaves out players and per, to get the rest
 *   king: { players: 1, features: { playing: { effects: { glowing: 1 } } } }   # features are optional, and only for this rank
 *   guard: {}
 * advancements: true          # optional; players earn advancements in its matches (default false)
 * quit:                       # optional; what quitting a running match does
 *   then: die                 # stay, die or out (the default)
 *   rejoin-seconds: 60        # how long they can come back; 0 (the default): not at all
 * arenas: [demo]
 * region: floor-tnt           # optional; plays in this region (or a list of them) of the arena, so one map can host several games: only the spawns inside are used
 * stats: [combat]             # optional; stat groups to keep, next to the game type's own stats
 * features:                   # optional; per phase of the game type, per feature
 *   playing:
 *     void-kills: { y: 0 }
 * hud:                        # optional; replaces parts of the HUD from config.yml, or false for none
 *   sidebar: { playing: [game, time, blank, kills] }
 * variants:                   # optional; each is played as its own choice
 *   lives:                    # only what differs; it needs its own name
 *     name: "&lt;gold&gt;Void Fall Lives"
 *     lives: 3
 * </pre>
 *
 * <p>A variant's keys replace the game's one by one, lists as a whole, and {@code <feature>: false}
 * turns off a feature of the game. Each game file stands on its own: games don't extend each other.
 */
public final class GameLoader {

    public static final String VARIANTS = "variants";

    private final IdRegistry<GameType> gametypes;
    private final IdRegistry<FeatureFactory> features;
    private final IdRegistry<StatGroup> statGroups;
    private final List<Check<Hosting>> checks;
    private final GameSettings settings;
    private final boolean strict;

    /**
     * @param checks what every arena a game lists goes through; it plays only in those with nothing blocking
     * @param messageKeys every message key, which a game may give its own wording for
     */
    public GameLoader(IdRegistry<GameType> gametypes, IdRegistry<FeatureFactory> features, IdRegistry<StatGroup> statGroups,
            List<Check<Hosting>> checks, Supplier<? extends Collection<String>> messageKeys) {
        this(gametypes, features, statGroups, checks, new GameSettings(gametypes, features, statGroups, messageKeys), false);
    }

    private GameLoader(IdRegistry<GameType> gametypes, IdRegistry<FeatureFactory> features, IdRegistry<StatGroup> statGroups,
            List<Check<Hosting>> checks, GameSettings settings, boolean strict) {
        this.gametypes = gametypes;
        this.features = features;
        this.statGroups = statGroups;
        this.checks = List.copyOf(checks);
        this.settings = settings;
        this.strict = strict;
    }

    /** For tests: this loader, failing as soon as a game, a game type or a feature reads a key that isn't declared. */
    public GameLoader strict() {
        return new GameLoader(gametypes, features, statGroups, checks, settings, true);
    }

    /** Every key a file of the game type can have; with an unknown game type, those that don't depend on it. */
    public List<Setting<?>> settings(String gametype) {
        return settings.of(gametypes.find(gametype));
    }

    /** The keys {@link #settings} offers for completion, every id spelled out, with their values. */
    public SortedMap<String, List<String>> keys(String gametype) {
        return new TreeMap<>(Setting.keys(settings(gametype), true));
    }

    private ConfigReader reader(String file, YamlConfiguration config) {
        return strict ? ConfigReader.strict(file, config, settings(config.getString(GameSettings.GAMETYPE, ""))) : ConfigReader.of(file, config);
    }

    /**
     * @param arenas the arenas loaded so far; a game may only use playable ones
     * @param hasLobby whether a lobby is set, which games that wait in the lobby need
     * @param hud the HUD every game has unless it sets its own under {@code hud}
     * @param knownMessage whether a message key exists, so a game's own wording for a mistyped key is caught
     */
    public YamlFiles.Loaded<Game> load(YamlFiles files, IdRegistry<Arena> arenas, boolean hasLobby, ConfigurationSection hud,
            Predicate<String> knownMessage) {
        var withHud = new TreeMap<String, YamlConfiguration>();
        // enabled: false keeps a file (documentation, work in progress) out of the games without deleting it.
        files.files().forEach((id, own) -> {
            if (own.getBoolean(GameSettings.ENABLED.key(), true)) {
                withHud.put(id, withHud(hud, own, null));
            }
        });
        return new YamlFiles(files.folder(), withHud, files.problems())
                .parse(Game::id, this::reader, (id, config) -> parseGame(id, config, files.fileName(id), files.files().get(id), arenas, hasLobby, hud, knownMessage));
    }

    /** The HUD defaults, the game's own settings over them, and the variant's over those if one is given. */
    private static YamlConfiguration withHud(ConfigurationSection hud, YamlConfiguration own, ConfigurationSection variant) {
        var merged = new YamlConfiguration();
        if (!hud.getKeys(false).isEmpty()) {
            YamlFiles.overlay(merged.createSection("hud"), hud);
        }
        YamlFiles.overlay(merged, own);
        merged.set(GameSettings.ENABLED.key(), null);
        if (variant != null) {
            merged.set(VARIANTS, null);
            YamlFiles.overlay(merged, variant);
        }
        return merged;
    }

    private Game parseGame(String id, ConfigReader config, String file, YamlConfiguration own, IdRegistry<Arena> arenas, boolean hasLobby,
            ConfigurationSection hud, Predicate<String> knownMessage) {
        config.refuse("extends", "games don't extend each other; put what differs under variants, or copy the other game's file");
        var base = parse(id, Optional.empty(), config, arenas, hasLobby, knownMessage);
        // Each variant is checked on its own below; here they only count as read.
        var section = config.has(VARIANTS) ? Optional.of(config.section(VARIANTS)) : Optional.<ConfigReader>empty();
        var ids = section.map(ConfigReader::keys).orElse(Set.of());
        ids.stream().filter(variant -> own.isConfigurationSection(VARIANTS + "." + variant))
                .forEach(variant -> section.orElseThrow().section(variant).keys());
        try {
            config.check();
        } catch (InvalidConfigException e) {
            // The variants would only repeat what is wrong with the game itself.
            return new Game(id, base, Map.of());
        }
        var variants = new HashMap<String, Variant>();
        for (String variant : ids) {
            var key = VARIANTS + "." + variant;
            if (!IdRegistry.isValid(variant)) {
                config.problem(key, "use lower case letters, digits and dashes");
                continue;
            }
            var settings = own.getConfigurationSection(key);
            if (settings == null) {
                config.problem(key, "must be the settings that differ from the game, such as { name: ..., lives: 3 }");
                continue;
            }
            if (!settings.isString(GameSettings.NAME.key())) {
                config.problem(key, "needs a name, so players can tell it from the game itself");
            }
            if (settings.contains(GameSettings.GAMETYPE)) {
                // Its stats count as the game's, so it has to be the same kind of game.
                config.problem(key + "." + GameSettings.GAMETYPE, "a variant plays its game's game type; make another game for another type");
                continue;
            }
            var reader = reader(file, withHud(hud, own, settings));
            variants.put(variant, parse(id, Optional.of(variant), reader, arenas, hasLobby, knownMessage));
            try {
                reader.check();
            } catch (InvalidConfigException e) {
                e.problems().forEach(problem -> config.problem(key + (problem.key().isEmpty() ? "" : "." + problem.key()), problem.message()));
            }
        }
        return new Game(id, base, variants);
    }

    /** @param variant empty for the game itself */
    private Variant parse(String id, Optional<String> variant, ConfigReader config, IdRegistry<Arena> arenas, boolean hasLobby,
            Predicate<String> knownMessage) {
        var gametypeId = config.read(settings.gametype());
        var game = gametypes.find(gametypeId).orElse(null);
        if (!gametypeId.isEmpty() && game == null) {
            config.problem(GameSettings.GAMETYPE, "unknown game type '" + gametypeId + "'; known: " + String.join(", ", gametypes.ids()));
        }
        if (game != null) {
            game = game.configure(config);
        }
        var type = Optional.ofNullable(game);
        var name = config.read(GameSettings.NAME).orElse(id);
        var minPlayers = config.read(GameSettings.MIN_PLAYERS);
        var maxPlayers = config.read(GameSettings.MAX_PLAYERS);
        if (maxPlayers < minPlayers) {
            config.problem(GameSettings.MAX_PLAYERS.key(), "must be at least min-players (" + minPlayers + ")");
        }
        var waiting = config.read(GameSettings.WAITING);
        if (waiting == Waiting.LOBBY && !hasLobby) {
            config.problem(GameSettings.WAITING.key(), "no lobby is set; set one with /mga lobby set or in config.yml");
        }
        var lives = config.read(GameSettings.LIVES).orElse(type.map(GameType::lives).orElse(0));
        var nameTags = config.read(GameSettings.NAME_TAGS);
        var messagesSetting = settings.messages();
        var messages = config.read(messagesSetting);
        messages.keySet().stream().filter(knownMessage.negate())
                .forEach(key -> config.problem(messagesSetting.key() + "." + key, "unknown message"));
        var teams = teams(config, minPlayers, maxPlayers);
        var spawns = Optional.of(GameSettings.SPAWNS.key()).filter(config::has).map(config::section);
        var teamSpawns = spawns.map(section -> section.read(GameSettings.TEAM_SPAWNS)).orElse(TeamSpawns.TOGETHER);
        var spawnMode = spawns.flatMap(section -> section.read(GameSettings.SPAWN_MODE));
        var forcedRandom = spawnMode.filter(mode -> mode == SpawnMode.RANDOM).isPresent();
        var randomSpawns = spawnMode.map(mode -> mode == SpawnMode.RANDOM).orElse(type.map(GameType::spawnsAnywhere).orElse(false));
        var roles = roles(config, minPlayers, game, teams);
        // The roles take their players first; only the rest form teams.
        teams.filter(setup -> setup.size() > 0).ifPresent(setup -> {
            var runners = maxPlayers - roles.stream().mapToInt(role -> role.share().wanted(maxPlayers)).sum();
            if (setup.teamsFor(runners) > TeamSetup.IDS.size()) {
                config.problem("teams.size", "max-players " + (runners == maxPlayers ? "" : "leaves " + runners + " players for teams, which ")
                        + "makes " + setup.teamsFor(runners) + " teams; at most " + TeamSetup.IDS.size() + " are possible");
            }
        });
        requiredTeams(config, game, teams, roles);
        var ranks = ranks(config, game, teams.isPresent() || !roles.isEmpty());
        var quit = quit(config);
        var mostTeams = roles.size() + teams.map(setup -> setup.teamsFor(maxPlayers)).orElse(roles.isEmpty() ? maxPlayers : 0);
        var listed = arenas(config, arenas);
        var features = features(config, game);
        var stats = statGroups(config);
        var changesBlocks = Variant.allFeatures(game, features, roles, ranks).anyMatch(Feature::changesBlocks);
        var usesLoot = Variant.allFeatures(game, features, roles, ranks).anyMatch(Feature::usesLootPoints);
        var region = config.read(GameSettings.REGION);
        var respawn = spawns.map(section -> section.read(GameSettings.RESPAWN)).orElse(Respawn.SPAWNS);
        if (respawn == Respawn.HIGHEST_FLOOR && region.isEmpty()) {
            config.problem(GameSettings.SPAWNS.key() + "." + GameSettings.RESPAWN.key(), "highest-floor needs the region the game plays in; set region");
        }
        var respawnSeconds = config.read(GameSettings.RESPAWN_SECONDS);
        var endingSeconds = config.read(GameSettings.ENDING_SECONDS);
        var featureRegions = Stream.concat(region.stream(), Variant.allFeatures(game, features, roles, ranks).flatMap(feature -> feature.regions().stream())).distinct().toList();
        var allFeatures = Variant.allFeatures(game, features, roles, ranks).toList();
        // Not a problem of the file: an arena that isn't ready is still being set up, and a problem would fail
        // the reload it needs to get ready. It is left out, and the findings say why.
        var playable = new ArrayList<Arena>();
        var findings = new ArrayList<Finding>();
        // With an unknown game type the file has a problem already, and nothing to check against.
        for (var whole : game == null ? List.<Arena>of() : listed) {
            // Without the region the check reports it missing; the arena stays whole for that.
            var played = region.stream().map(whole.regions()::get).filter(java.util.Objects::nonNull).toList();
            var inRegion = played.isEmpty() ? whole : whole.within(played);
            // Written out, spawns.mode random beats the arena's spawns (made for other games); by default it only stands in for missing ones.
            var arena = forcedRandom && !inRegion.area().isEmpty() ? inRegion.withoutSpawns() : inRegion;
            var diagnosis = Diagnosis.run(Variant.key(id, variant), arena.id(), new Hosting(arena, game, mostTeams, teams.isPresent(), changesBlocks, featureRegions, !(randomSpawns && !arena.area().isEmpty()) && (game.usesSpawns() || waiting == Waiting.ARENA && arena.waitingSpot().isEmpty()), usesLoot, allFeatures), checks);
            if (diagnosis.isReady()) {
                playable.add(arena);
            }
            findings.addAll(diagnosis.findings());
        }
        var hud = Hud.read(config, game == null ? List.of() : game.phases().stream().map(Phase::id).toList(), knownStats());
        return new Variant(id, variant, game, name, minPlayers, maxPlayers, waiting, lives, nameTags, messages, teams, teamSpawns, randomSpawns, respawn, respawnSeconds, endingSeconds, roles, ranks, quit, playable, findings,
                features, stats, hud, config.read(GameSettings.ADVANCEMENTS));
    }

    /** Every stat a game or stat group keeps. */
    private Set<String> knownStats() {
        return Stream.concat(gametypes.ids().stream().flatMap(id -> gametypes.find(id).orElseThrow().stats().stream()),
                statGroups.ids().stream().flatMap(id -> statGroups.find(id).orElseThrow().stats().stream()))
                .map(Stat::id).collect(Collectors.toSet());
    }

    private List<StatGroup> statGroups(ConfigReader config) {
        var setting = settings.stats();
        var groups = new ArrayList<StatGroup>();
        for (String id : config.read(setting)) {
            statGroups.find(id).ifPresentOrElse(groups::add,
                    () -> config.problem(setting.key(), "unknown stat group '" + id + "'; known: " + String.join(", ", statGroups.ids())));
        }
        return groups;
    }

    private static Quit quit(ConfigReader config) {
        var quit = config.read(GameSettings.QUIT);
        var then = quit.read(GameSettings.QUIT_THEN);
        var seconds = quit.read(GameSettings.QUIT_REJOIN_SECONDS);
        if (then == Quit.Then.STAY && seconds == 0) {
            quit.problem(GameSettings.QUIT_THEN.key(), "stay needs rejoin-seconds above 0; without a way back quitting puts them out");
        }
        return new Quit(then, seconds);
    }

    private static Optional<TeamSetup> teams(ConfigReader config, int minPlayers, int maxPlayers) {
        if (!config.has(GameSettings.TEAMS.key())) {
            return Optional.empty();
        }
        var teams = config.read(GameSettings.TEAMS);
        var countKey = GameSettings.TEAM_COUNT.key();
        if (teams.has(countKey) == teams.has(GameSettings.TEAM_SIZE.key())) {
            config.problem(GameSettings.TEAMS.key(), "set either count or size");
            teams.keys();
            return Optional.empty();
        }
        var roles = config.has(GameSettings.ROLES);
        if (teams.has(countKey)) {
            // Beside roles one team is a match: the roles are the other side, so the least count depends on them.
            var count = teams.integer(countKey, roles ? 1 : 2, TeamSetup.IDS.size());
            if (minPlayers < count) {
                config.problem(GameSettings.MIN_PLAYERS.key(), "must be at least the number of teams (" + count + ")");
            }
            return Optional.of(TeamSetup.ofCount(count));
        }
        var size = teams.read(GameSettings.TEAM_SIZE);
        // With a single team the match would be decided the moment it starts, unless roles play against it.
        if (!roles && minPlayers <= size) {
            config.problem(GameSettings.MIN_PLAYERS.key(), "must be more than the team size (" + size + "), so there are at least two teams");
        }
        return Optional.of(TeamSetup.ofSize(size));
    }

    private List<Role> roles(ConfigReader config, int minPlayers, GameType game, Optional<TeamSetup> teams) {
        var needed = game == null ? List.<String>of() : game.roles();
        if (!config.has(GameSettings.ROLES)) {
            if (!needed.isEmpty()) {
                config.problem(GameSettings.ROLES, game.id() + " needs the roles " + String.join(", ", needed));
            }
            return List.of();
        }
        var section = config.section(GameSettings.ROLES);
        var roles = new ArrayList<Role>();
        for (String id : section.keys()) {
            if (!IdRegistry.isValid(id)) {
                section.problem(id, "use lower case letters, digits and dashes");
            }
            var role = section.section(id);
            var name = role.read(RoleSettings.NAME).orElse(id);
            var color = NamedTextColor.NAMES.valueOrThrow(TeamSetup.IDS.get(roles.size() % TeamSetup.IDS.size()));
            var text = role.read(RoleSettings.COLOR);
            if (text.isPresent()) {
                var named = NamedTextColor.NAMES.value(text.get());
                if (named == null) {
                    role.problem(RoleSettings.COLOR.key(), "unknown colour '" + text.get() + "'; colours: " + String.join(", ", NamedTextColor.NAMES.keys()));
                } else {
                    color = named;
                }
            }
            var share = RoleSettings.share(role);
            var lives = role.read(RoleSettings.LIVES).map(OptionalInt::of).orElse(OptionalInt.empty());
            var onOut = role.read(RoleSettings.ON_OUT);
            var others = section.keys().stream().filter(other -> !other.equals(id)).toList();
            if (onOut.isPresent() && !others.contains(onOut.get())) {
                role.problem(RoleSettings.ON_OUT.key(), "must be one of the other roles: " + String.join(", ", others));
            }
            roles.add(new Role(id, name, color, share, lives, onOut, role.read(RoleSettings.NAME_TAGS), features(role, game)));
        }
        var ids = roles.stream().map(Role::id).toList();
        if (!needed.isEmpty() && !Set.copyOf(ids).equals(Set.copyOf(needed))) {
            var missing = needed.stream().filter(id -> !ids.contains(id)).toList();
            var unknown = ids.stream().filter(id -> !needed.contains(id)).toList();
            config.problem("roles", game.id() + " needs exactly the roles " + String.join(", ", needed)
                    + (missing.isEmpty() ? "" : "; missing: " + String.join(", ", missing))
                    + (unknown.isEmpty() ? "" : "; unknown: " + String.join(", ", unknown)));
        }
        if (roles.size() < 2 && teams.isEmpty()) {
            config.problem("roles", "needs at least two roles");
        }
        var rest = roles.stream().filter(role -> role.share().isRest()).map(Role::id).toList();
        if (teams.isPresent()) {
            var asked = roles.stream().mapToInt(role -> role.share().wanted(minPlayers)).sum();
            var teamsNeed = teams.get().count() > 0 ? teams.get().count() : 1;
            if (!rest.isEmpty()) {
                config.problem("roles", "with teams, the players left after the roles form the teams; give players or per to "
                        + String.join(", ", rest));
            } else if (minPlayers < asked + teamsNeed) {
                config.problem("min-players", "must be at least " + (asked + teamsNeed) + ": the roles ask for " + asked
                        + " players and the teams need " + teamsNeed + " more");
            }
        } else if (rest.size() != 1) {
            config.problem("roles", "exactly one role must leave out players and per, to get everyone left over; "
                    + (rest.isEmpty() ? "none does" : String.join(", ", rest) + " do"));
        } else {
            var asked = roles.stream().mapToInt(role -> role.share().wanted(minPlayers)).sum();
            if (asked >= minPlayers) {
                config.problem("min-players", "must be more than the " + asked + " players the other roles ask for, so " + rest.getFirst()
                        + " gets at least one");
            }
        }
        return roles;
    }

    /** A game written for a number of teams needs exactly that many: its roles plus the teams of a fixed count. */
    private static void requiredTeams(ConfigReader config, GameType game, Optional<TeamSetup> teams, List<Role> roles) {
        if (game == null || game.requiredTeams().isEmpty()) {
            return;
        }
        var wanted = game.requiredTeams().getAsInt();
        // Teams of a fixed size make a number of teams that depends on the players, so they have no count to compare.
        var have = roles.size() + teams.map(TeamSetup::count).orElse(0);
        if (have != wanted) {
            config.problem("teams", game.id() + " needs exactly " + wanted + " teams; set teams: { count: " + wanted + " }"
                    + (have > 0 ? ", it has " + have : ""));
        }
    }

    /**
     * The ranks that split each team: a fixed number or one per so many players, and one rank that gets whoever is left. The game
     * type's own ranks must all be defined.
     */
    private List<Rank> ranks(ConfigReader config, GameType game, boolean hasTeams) {
        var needed = game == null ? List.<String>of() : game.ranks();
        if (!config.has(GameSettings.RANKS)) {
            if (!needed.isEmpty()) {
                config.problem(GameSettings.RANKS, game.id() + " needs the ranks " + String.join(", ", needed));
            }
            return List.of();
        }
        if (!hasTeams) {
            config.problem(GameSettings.RANKS, "ranks split a team; set teams or roles");
        }
        var section = config.section(GameSettings.RANKS);
        var ranks = new ArrayList<Rank>();
        for (String id : section.keys()) {
            if (!IdRegistry.isValid(id)) {
                section.problem(id, "use lower case letters, digits and dashes");
            }
            var rank = section.section(id);
            ranks.add(new Rank(id, RoleSettings.share(rank), features(rank, game)));
        }
        var ids = ranks.stream().map(Rank::id).toList();
        var missing = needed.stream().filter(id -> !ids.contains(id)).toList();
        if (!missing.isEmpty()) {
            config.problem("ranks", game.id() + " needs the ranks " + String.join(", ", needed) + "; missing: " + String.join(", ", missing));
        }
        var rest = ranks.stream().filter(rank -> rank.share().isRest()).map(Rank::id).toList();
        if (rest.size() != 1) {
            config.problem("ranks", "exactly one rank must leave out players and per, to get everyone left over; "
                    + (rest.isEmpty() ? "none does" : String.join(", ", rest) + " do"));
        }
        return ranks;
    }

    /** The listed arenas that loaded, ready or not. */
    private static List<Arena> arenas(ConfigReader config, IdRegistry<Arena> registry) {
        var key = GameSettings.ARENAS.key();
        var ids = config.read(GameSettings.ARENAS);
        if (ids.isEmpty() && config.has(key)) {
            config.problem(key, "needs at least one arena");
        }
        var arenas = new ArrayList<Arena>();
        for (String id : ids) {
            registry.find(id).ifPresentOrElse(arenas::add,
                    () -> config.problem(key, "no valid arena '" + id + "'; arenas: " + String.join(", ", registry.ids())));
        }
        return arenas;
    }

    /** The {@code features} of the game, a role or a rank, by phase. */
    private Map<String, List<Feature>> features(ConfigReader owner, GameType game) {
        if (!owner.has(GameSettings.FEATURES)) {
            return Map.of();
        }
        var config = owner.section(GameSettings.FEATURES);
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
                if (phaseConfig.isOff(featureId)) {
                    continue;
                }
                var factory = features.find(featureId);
                if (factory.isEmpty()) {
                    phaseConfig.problem(featureId, "unknown feature; known: " + String.join(", ", features.ids()));
                } else {
                    var feature = factory.get().create(phaseConfig.section(featureId));
                    if (feature.needsPlayersToBuild() && game != null && game.gameMode(phase) == GameMode.ADVENTURE) {
                        phaseConfig.problem(featureId, "changes blocks, but " + game.id() + " keeps players in adventure mode in " + phase + ", where they can't");
                    }
                    phaseFeatures.add(feature);
                }
            }
            byPhase.put(phase, List.copyOf(phaseFeatures));
        }
        return byPhase;
    }
}
