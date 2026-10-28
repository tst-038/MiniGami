package io.github.tst_038.minigami.paper.mode;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.team.RoleShare;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Turns {@code modes/<id>.yml} files into modes:
 *
 * <pre>
 * game: last-standing
 * name: "&lt;gold&gt;Void Fall"   # optional, defaults to the id
 * min-players: 2
 * max-players: 8
 * waiting: arena              # in-place, lobby or arena
 * teams: { count: 2 }         # optional; or { size: 2 }, teams of two
 * roles:                      # optional, instead of teams; handed out in this order
 *   seeker: { name: "Seekers", color: red, players: 1, lives: 0 }   # or per: 5, one per five players
 *   hider:                                                         # neither: everyone left over
 *     name: "Hiders"
 *     on-out: seeker                                              # optional; joins it instead of going out
 *     features: { playing: { kit: { items: [stick] } } }          # optional; only for this role
 * arenas: [demo]
 * features:                   # optional; per phase of the game, per feature
 *   playing:
 *     void-kills: { y: 0 }
 * </pre>
 *
 * <p>A mode can {@code extends: <mode>} and set only what differs: its keys replace the other mode's
 * one by one, lists as a whole, and {@code <feature>: false} turns off a feature it would inherit.
 */
public final class ModeLoader {

    private final IdRegistry<GameType> games;
    private final IdRegistry<FeatureFactory> features;

    public ModeLoader(IdRegistry<GameType> games, IdRegistry<FeatureFactory> features) {
        this.games = games;
        this.features = features;
    }

    /**
     * @param arenas the arenas loaded so far; a mode may only use playable ones
     * @param hasLobby whether a lobby is set, which modes that wait in the lobby need
     */
    public YamlFiles.Loaded<Mode> load(YamlFiles files, IdRegistry<Arena> arenas, boolean hasLobby) {
        var own = files.files();
        var merged = new TreeMap<String, YamlConfiguration>();
        var extendsProblems = new HashMap<String, String>();
        own.keySet().forEach(id -> merged.put(id, withParents(id, own, new ArrayList<>(), extendsProblems)));
        return new YamlFiles(files.folder(), merged, files.problems())
                .parse(Mode::id, (id, config) -> parse(id, config, own.keySet(), extendsProblems.get(id), arenas, hasLobby));
    }

    /** The mode's file over the modes it extends; a broken link is left out, as {@link #load} reports it. */
    public static YamlConfiguration inherited(String id, Map<String, YamlConfiguration> files) {
        return withParents(id, files, new ArrayList<>(), new HashMap<>());
    }

    /**
     * The mode's file over the modes it extends. A link to a missing mode, or back into the chain, is
     * left out and reported for the mode at the start of the chain.
     */
    private static YamlConfiguration withParents(String id, Map<String, YamlConfiguration> files, List<String> chain,
            Map<String, String> problems) {
        var own = files.get(id);
        var parent = own.getString("extends");
        chain.add(id);
        if (parent == null) {
            return own;
        }
        if (!files.containsKey(parent)) {
            problems.put(chain.getFirst(), "no mode '" + parent + "'" + (chain.size() > 1 ? " in " + String.join(" > ", chain) : ""));
            return own;
        }
        if (chain.contains(parent)) {
            problems.put(chain.getFirst(), "goes round in a circle: " + String.join(" > ", chain) + " > " + parent);
            return own;
        }
        var merged = new YamlConfiguration();
        overlay(merged, withParents(parent, files, chain, problems));
        overlay(merged, own);
        return merged;
    }

    private static void overlay(ConfigurationSection target, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            if (source.get(key) instanceof ConfigurationSection child) {
                overlay(target.get(key) instanceof ConfigurationSection existing ? existing : target.createSection(key), child);
            } else {
                target.set(key, source.get(key));
            }
        }
    }

    /** @param extendsProblem what is wrong with the mode it extends, if anything */
    private Mode parse(String id, ConfigReader config, Set<String> modes, String extendsProblem, IdRegistry<Arena> arenas, boolean hasLobby) {
        config.suggest("extends", modes);
        if (config.has("extends")) {
            config.string("extends");
            if (extendsProblem != null) {
                config.problem("extends", extendsProblem);
            }
        }
        config.suggest("game", games.ids());
        var gameId = config.string("game");
        var game = games.find(gameId).orElse(null);
        if (!gameId.isEmpty() && game == null) {
            config.problem("game", "unknown game '" + gameId + "'; known: " + String.join(", ", games.ids()));
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
        var teams = teams(config, minPlayers, maxPlayers);
        var roles = roles(config, minPlayers, game);
        var mostTeams = !roles.isEmpty() ? roles.size() : teams.map(setup -> setup.teamsFor(maxPlayers)).orElse(maxPlayers);
        return new Mode(id, game, name, minPlayers, maxPlayers, waiting, lives, teams, roles, arenas(config, arenas, game, mostTeams),
                config.has("features") ? features(config.section("features"), game) : Map.of());
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
            roles.add(new Role(id, name, color, share, lives, onOut, role.has("features") ? features(role.section("features"), game) : Map.of()));
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

    /**
     * @param game checks its arenas have the regions it needs; may be null when unknown
     * @param mostTeams how many teams a match can have, for the game's region per team
     */
    private static List<Arena> arenas(ConfigReader config, IdRegistry<Arena> registry, GameType game, int mostTeams) {
        var ids = config.strings("arenas");
        if (ids.isEmpty() && config.has("arenas")) {
            config.problem("arenas", "needs at least one arena");
        }
        var arenas = new ArrayList<Arena>();
        for (String id : ids) {
            var arena = registry.find(id);
            if (arena.isEmpty()) {
                config.problem("arenas", "no valid arena '" + id + "'; arenas: " + String.join(", ", registry.ids()));
            } else if (arena.get().isPlayable()) {
                arenas.add(arena.get());
                var needed = game == null ? List.<String>of() : Stream.concat(game.regions().stream(), game.regionPerTeam().stream()
                        .flatMap(prefix -> IntStream.rangeClosed(1, mostTeams).mapToObj(number -> prefix + "-" + number))).toList();
                var missing = needed.stream().filter(region -> !arena.get().regions().containsKey(region)).toList();
                if (!missing.isEmpty()) {
                    config.problem("arenas", "arena '" + id + "' lacks the regions " + game.id() + " needs: " + String.join(", ", missing)
                            + "; save them with /mg arena " + id + " region <name> set");
                }
            }
            // Left out without a problem: an arena without spawns is still being set up, and a problem would fail the reload it needs to get them.
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
                    phaseFeatures.add(factory.get().create(phaseConfig.section(featureId)));
                }
            }
            byPhase.put(phase, List.copyOf(phaseFeatures));
        }
        return byPhase;
    }
}
