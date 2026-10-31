package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
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
import io.github.tst_038.minigami.core.team.RoleShare;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.diagnosis.Hosting;
import io.github.tst_038.minigami.paper.hud.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
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
 * team-spawns: spread        # optional; together (the default): a team shares one spawn, or spread: every player takes the next one
 * name-tags: teammates       # optional; who sees a player's name tag: everyone (the default), teammates or nobody
 * messages:                  # optional; the game's own wording for any message key, over messages/en.yml
 *   hide-and-seek:
 *     hiding: "%prefix%<green>Run!"
 * roles:                      # optional, instead of teams; handed out in this order
 *   seeker: { name: "Seekers", color: red, players: 1, lives: 0 }   # or per: 5, one per five players
 *   hider:                                                         # neither: everyone left over
 *     name: "Hiders"
 *     on-out: seeker                                              # optional; joins it instead of going out
 *     name-tags: teammates                                        # optional; the role's own, over the game's
 *     features: { playing: { kit: { items: [stick] } } }          # optional; only for this role
 * quit:                       # optional; what quitting a running match does
 *   then: die                 # stay, die or out (the default)
 *   rejoin-seconds: 60        # how long they can come back; 0 (the default): not at all
 * arenas: [demo]
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

    /** @param checks what every arena a game lists goes through; it plays only in those with nothing blocking */
    public GameLoader(IdRegistry<GameType> gametypes, IdRegistry<FeatureFactory> features, IdRegistry<StatGroup> statGroups,
            List<Check<Hosting>> checks) {
        this.gametypes = gametypes;
        this.features = features;
        this.statGroups = statGroups;
        this.checks = List.copyOf(checks);
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
        files.files().forEach((id, own) -> withHud.put(id, withHud(hud, own, null)));
        return new YamlFiles(files.folder(), withHud, files.problems())
                .parse(Game::id, (id, config) -> parseGame(id, config, files.fileName(id), files.files().get(id), arenas, hasLobby, hud, knownMessage));
    }

    /** The HUD defaults, the game's own settings over them, and the variant's over those if one is given. */
    private static YamlConfiguration withHud(ConfigurationSection hud, YamlConfiguration own, ConfigurationSection variant) {
        var merged = new YamlConfiguration();
        if (!hud.getKeys(false).isEmpty()) {
            YamlFiles.overlay(merged.createSection("hud"), hud);
        }
        YamlFiles.overlay(merged, own);
        if (variant != null) {
            merged.set(VARIANTS, null);
            YamlFiles.overlay(merged, variant);
        }
        return merged;
    }

    private Game parseGame(String id, ConfigReader config, String file, YamlConfiguration own, IdRegistry<Arena> arenas, boolean hasLobby,
            ConfigurationSection hud, Predicate<String> knownMessage) {
        if (config.has("extends")) {
            config.string("extends");
            config.problem("extends", "games don't extend each other; put what differs under variants, or copy the other game's file");
        }
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
            if (!settings.isString("name")) {
                config.problem(key, "needs a name, so players can tell it from the game itself");
            }
            if (settings.contains("gametype")) {
                // Its stats count as the game's, so it has to be the same kind of game.
                config.problem(key + ".gametype", "a variant plays its game's game type; make another game for another type");
                continue;
            }
            var reader = ConfigReader.of(file, withHud(hud, own, settings));
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
        config.suggest("gametype", gametypes.ids());
        var gametypeId = config.string("gametype");
        var game = gametypes.find(gametypeId).orElse(null);
        if (!gametypeId.isEmpty() && game == null) {
            config.problem("gametype", "unknown game type '" + gametypeId + "'; known: " + String.join(", ", gametypes.ids()));
        }
        if (game != null) {
            game = game.configure(config);
        }
        var name = config.has("name") ? config.string("name") : id;
        var minPlayers = config.integer("min-players", 1, Integer.MAX_VALUE);
        var maxPlayers = config.integer("max-players", 1, Integer.MAX_VALUE);
        if (maxPlayers < minPlayers) {
            config.problem("max-players", "must be at least min-players (" + minPlayers + ")");
        }
        var waiting = config.choice("waiting", Waiting.class);
        if (waiting == Waiting.LOBBY && !hasLobby) {
            config.problem("waiting", "no lobby is set; set one with /mg lobby set or in config.yml");
        }
        var lives = config.has("lives") ? config.integer("lives", 0, Integer.MAX_VALUE) : game == null ? 0 : game.lives();
        var nameTags = config.has("name-tags") ? config.choice("name-tags", NameTags.class) : NameTags.EVERYONE;
        var messages = config.has("messages") ? config.texts("messages") : Map.<String, String>of();
        messages.keySet().stream().filter(knownMessage.negate())
                .forEach(key -> config.problem("messages." + key, "unknown message"));
        var teams = teams(config, minPlayers, maxPlayers);
        var teamSpawns = config.has("team-spawns") ? config.choice("team-spawns", TeamSpawns.class) : TeamSpawns.TOGETHER;
        var roles = roles(config, minPlayers, game);
        var quit = quit(config);
        var mostTeams = !roles.isEmpty() ? roles.size() : teams.map(setup -> setup.teamsFor(maxPlayers)).orElse(maxPlayers);
        var listed = arenas(config, arenas);
        var features = config.has("features") ? features(config.section("features"), game) : Map.<String, List<Feature>>of();
        var stats = statGroups(config);
        var changesBlocks = Variant.allFeatures(game, features, roles).anyMatch(Feature::changesBlocks);
        // Not a problem of the file: an arena that isn't ready is still being set up, and a problem would fail
        // the reload it needs to get ready. It is left out, and the findings say why.
        var playable = new ArrayList<Arena>();
        var findings = new ArrayList<Finding>();
        // With an unknown game type the file has a problem already, and nothing to check against.
        for (var arena : game == null ? List.<Arena>of() : listed) {
            var diagnosis = Diagnosis.run(Variant.key(id, variant), arena.id(), new Hosting(arena, game, mostTeams, teams.isPresent(), changesBlocks), checks);
            if (diagnosis.isReady()) {
                playable.add(arena);
            }
            findings.addAll(diagnosis.findings());
        }
        var hud = Hud.read(config, game == null ? List.of() : game.phases().stream().map(Phase::id).toList(), knownStats());
        return new Variant(id, variant, game, name, minPlayers, maxPlayers, waiting, lives, nameTags, messages, teams, teamSpawns, roles, quit, playable, findings,
                features, stats, hud);
    }

    /** Every stat a game or stat group keeps. */
    private Set<String> knownStats() {
        return Stream.concat(gametypes.ids().stream().flatMap(id -> gametypes.find(id).orElseThrow().stats().stream()),
                statGroups.ids().stream().flatMap(id -> statGroups.find(id).orElseThrow().stats().stream()))
                .map(Stat::id).collect(Collectors.toSet());
    }

    private List<StatGroup> statGroups(ConfigReader config) {
        config.suggest("stats", statGroups.ids());
        var groups = new ArrayList<StatGroup>();
        if (!config.has("stats")) {
            return groups;
        }
        for (String id : config.strings("stats")) {
            statGroups.find(id).ifPresentOrElse(groups::add,
                    () -> config.problem("stats", "unknown stat group '" + id + "'; known: " + String.join(", ", statGroups.ids())));
        }
        return groups;
    }

    private static Quit quit(ConfigReader config) {
        config.suggest("quit.then", List.of("stay", "die", "out"));
        config.suggest("quit.rejoin-seconds", List.of("60"));
        if (!config.has("quit")) {
            return Quit.OUT;
        }
        var quit = config.section("quit");
        var then = quit.has("then") ? quit.choice("then", Quit.Then.class) : Quit.Then.OUT;
        var seconds = quit.has("rejoin-seconds") ? quit.integer("rejoin-seconds", 0, 3600) : 0;
        if (then == Quit.Then.STAY && seconds == 0) {
            quit.problem("then", "stay needs rejoin-seconds above 0; without a way back quitting puts them out");
        }
        return new Quit(then, seconds);
    }

    private static Optional<TeamSetup> teams(ConfigReader config, int minPlayers, int maxPlayers) {
        config.suggest("teams.count", List.of("2"));
        config.suggest("teams.size", List.of("2"));
        if (!config.has("teams")) {
            return Optional.empty();
        }
        var teams = config.section("teams");
        if (teams.has("count") == teams.has("size")) {
            config.problem("teams", "set either count or size");
            teams.keys();
            return Optional.empty();
        }
        var most = TeamSetup.IDS.size();
        if (teams.has("count")) {
            var count = teams.integer("count", 2, most);
            if (minPlayers < count) {
                config.problem("min-players", "must be at least the number of teams (" + count + ")");
            }
            return Optional.of(TeamSetup.ofCount(count));
        }
        var size = teams.integer("size", 1, Integer.MAX_VALUE);
        if (size == 1) {
            teams.problem("size", "teams of one are free for all; leave out teams instead");
            return Optional.empty();
        }
        var setup = TeamSetup.ofSize(size);
        // With a single team the match would be decided the moment it starts.
        if (minPlayers <= size) {
            config.problem("min-players", "must be more than the team size (" + size + "), so there are at least two teams");
        }
        if (setup.teamsFor(maxPlayers) > most) {
            teams.problem("size", "max-players makes " + setup.teamsFor(maxPlayers) + " teams; at most " + most + " are possible");
        }
        return Optional.of(setup);
    }

    private List<Role> roles(ConfigReader config, int minPlayers, GameType game) {
        var needed = game == null ? List.<String>of() : game.roles();
        needed.forEach(id -> config.suggest("roles." + id, List.of()));
        if (!config.has("roles")) {
            if (!needed.isEmpty()) {
                config.problem("roles", game.id() + " needs the roles " + String.join(", ", needed));
            }
            return List.of();
        }
        if (config.has("teams")) {
            config.problem("roles", "set either teams or roles");
        }
        var section = config.section("roles");
        var roles = new ArrayList<Role>();
        for (String id : section.keys()) {
            if (!IdRegistry.isValid(id)) {
                section.problem(id, "use lower case letters, digits and dashes");
            }
            var role = section.section(id);
            var name = role.has("name") ? role.string("name") : id;
            role.suggest("color", NamedTextColor.NAMES.keys());
            var color = NamedTextColor.NAMES.valueOrThrow(TeamSetup.IDS.get(roles.size() % TeamSetup.IDS.size()));
            if (role.has("color")) {
                var text = role.string("color");
                var named = NamedTextColor.NAMES.value(text);
                if (named == null) {
                    role.problem("color", "unknown colour '" + text + "'; colours: " + String.join(", ", NamedTextColor.NAMES.keys()));
                } else {
                    color = named;
                }
            }
            if (role.has("players") && role.has("per")) {
                role.problem("per", "set either players or per");
                role.keys();
            }
            var share = role.has("players") ? RoleShare.of(role.integer("players", 1, Integer.MAX_VALUE))
                    : role.has("per") ? RoleShare.onePer(role.integer("per", 2, Integer.MAX_VALUE))
                    : RoleShare.rest();
            var lives = role.has("lives") ? OptionalInt.of(role.integer("lives", 0, Integer.MAX_VALUE)) : OptionalInt.empty();
            var onOut = role.has("on-out") ? Optional.of(role.string("on-out")) : Optional.<String>empty();
            var others = section.keys().stream().filter(other -> !other.equals(id)).toList();
            if (onOut.isPresent() && !others.contains(onOut.get())) {
                role.problem("on-out", "must be one of the other roles: " + String.join(", ", others));
            }
            var nameTags = role.has("name-tags") ? Optional.of(role.choice("name-tags", NameTags.class)) : Optional.<NameTags>empty();
            roles.add(new Role(id, name, color, share, lives, onOut, nameTags, role.has("features") ? features(role.section("features"), game) : Map.of()));
        }
        var ids = roles.stream().map(Role::id).toList();
        if (!needed.isEmpty() && !Set.copyOf(ids).equals(Set.copyOf(needed))) {
            var missing = needed.stream().filter(id -> !ids.contains(id)).toList();
            var unknown = ids.stream().filter(id -> !needed.contains(id)).toList();
            config.problem("roles", game.id() + " needs exactly the roles " + String.join(", ", needed)
                    + (missing.isEmpty() ? "" : "; missing: " + String.join(", ", missing))
                    + (unknown.isEmpty() ? "" : "; unknown: " + String.join(", ", unknown)));
        }
        if (roles.size() < 2) {
            config.problem("roles", "needs at least two roles");
        }
        var rest = roles.stream().filter(role -> role.share().isRest()).map(Role::id).toList();
        if (rest.size() != 1) {
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

    /** The listed arenas that loaded, ready or not. */
    private static List<Arena> arenas(ConfigReader config, IdRegistry<Arena> registry) {
        var ids = config.strings("arenas");
        if (ids.isEmpty() && config.has("arenas")) {
            config.problem("arenas", "needs at least one arena");
        }
        var arenas = new ArrayList<Arena>();
        for (String id : ids) {
            registry.find(id).ifPresentOrElse(arenas::add,
                    () -> config.problem("arenas", "no valid arena '" + id + "'; arenas: " + String.join(", ", registry.ids())));
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
                if (phaseConfig.isOff(featureId)) {
                    continue;
                }
                var factory = features.find(featureId);
                if (factory.isEmpty()) {
                    phaseConfig.problem(featureId, "unknown feature; known: " + String.join(", ", features.ids()));
                } else {
                    var feature = factory.get().create(phaseConfig.section(featureId));
                    if (feature.changesBlocks() && game != null && game.gameMode(phase) == GameMode.ADVENTURE) {
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
