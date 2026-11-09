package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.paper.hud.HudSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The keys of a game file, each declared once: what completion offers, what info lists and what a strict reader allows.
 * The keys that depend on the game type, such as its phases and roles, are made per game type.
 */
final class GameSettings {

    static final String GAMETYPE = "gametype";
    static final String FEATURES = "features";
    static final String ROLES = "roles";
    static final String RANKS = "ranks";

    static final Setting<Boolean> ENABLED = Setting.of("enabled", Kind.bool(), "false keeps the file out of the games without deleting it")
            .orElse(true);
    static final Setting<Optional<String>> NAME = Setting.of("name", Kind.optional(Kind.text()), "the name players see; left out, the id")
            .orElse(Optional.empty());
    static final Setting<Integer> MIN_PLAYERS = Setting.of("min-players", Kind.integer(1, Integer.MAX_VALUE), "the fewest players a match starts with");
    static final Setting<Integer> MAX_PLAYERS = Setting.of("max-players", Kind.integer(1, Integer.MAX_VALUE), "the most players a match takes");
    static final Setting<Waiting> WAITING = Setting.of("waiting", Kind.choice(Waiting.class), "where players wait for the start");
    static final Setting<Optional<Integer>> LIVES = Setting.of("lives", Kind.optional(Kind.integer(0, Integer.MAX_VALUE)),
            "lives per player; left out, the game type's").orElse(Optional.empty());
    static final Setting<NameTags> NAME_TAGS = Setting.of("name-tags", Kind.choice(NameTags.class), "who sees a player's name tag")
            .orElse(NameTags.EVERYONE);
    static final Setting<Integer> TEAM_COUNT = Setting.of("count", Kind.integer(1, TeamSetup.IDS.size()), "the number of teams; instead of size");
    static final Setting<Integer> TEAM_SIZE = Setting.of("size", Kind.integer(1, Integer.MAX_VALUE), "players per team; instead of count");
    static final Setting<ConfigReader> TEAMS = Setting.of("teams", Kind.section(List.of(TEAM_COUNT, TEAM_SIZE)),
            "splits the players into teams, by count or by size");
    static final Setting<TeamSpawns> TEAM_SPAWNS = Setting.of("team-spawns", Kind.choice(TeamSpawns.class),
            "together: a team shares one spawn; spread: every player takes the next one").orElse(TeamSpawns.TOGETHER);
    static final Setting<Optional<Boolean>> RANDOM_SPAWNS = Setting.of("random-spawns", Kind.optional(Kind.bool()),
            "with no spawns set, players start on random places of the highest floor; left out, the game type's").orElse(Optional.empty());
    static final Setting<Respawn> RESPAWN = Setting.of("respawn", Kind.choice(Respawn.class),
            "where players come back: their spawns, or the highest floor still standing in region").orElse(Respawn.SPAWNS);
    static final Setting<Integer> RESPAWN_SECONDS = Setting.of("respawn-seconds", Kind.integer(0, 3600),
            "how long a player who died waits to come back; 0: at once").orElse(0);
    static final Setting<List<String>> REGION = Setting.of("region", Kind.stringsOrOne(),
            "plays in this region of the arena, or these, so one map can host several games").orElse(List.of());
    static final Setting<List<String>> ARENAS = Setting.of("arenas", Kind.strings(), "the arenas it plays in");
    static final Setting<Boolean> ADVANCEMENTS = Setting.of("advancements", Kind.bool(), "players earn advancements in its matches")
            .orElse(false);
    static final Setting<Quit.Then> QUIT_THEN = Setting.of("then", Kind.choice(Quit.Then.class), "what quitting does to the player")
            .orElse(Quit.Then.OUT);
    static final Setting<Integer> QUIT_REJOIN_SECONDS = Setting.of("rejoin-seconds", Kind.integer(0, 3600),
            "how long they can come back; 0: not at all").orElse(0);
    static final Setting<ConfigReader> QUIT = Setting.of("quit", Kind.section(List.of(QUIT_THEN, QUIT_REJOIN_SECONDS)),
            "what quitting a running match does");

    private final IdRegistry<GameType> gametypes;
    private final IdRegistry<FeatureFactory> features;
    private final IdRegistry<StatGroup> statGroups;
    private final Supplier<? extends Collection<String>> messageKeys;

    GameSettings(IdRegistry<GameType> gametypes, IdRegistry<FeatureFactory> features, IdRegistry<StatGroup> statGroups,
            Supplier<? extends Collection<String>> messageKeys) {
        this.gametypes = gametypes;
        this.features = features;
        this.statGroups = statGroups;
        this.messageKeys = messageKeys;
    }

    Setting<String> gametype() {
        return Setting.of(GAMETYPE, Kind.text(gametypes::ids), "the game type it plays");
    }

    Setting<List<String>> stats() {
        return Setting.of("stats", Kind.strings(statGroups.ids()), "stat groups to keep, next to the game type's own stats").orElse(List.of());
    }

    Setting<Map<String, String>> messages() {
        return Setting.of("messages", Kind.texts("message", messageKeys), "the game's own wording for any message key").orElse(Map.of());
    }

    /** Every key of a file of this game type; with an unknown one, those that don't depend on it. */
    List<Setting<?>> of(Optional<GameType> game) {
        var phases = game.map(type -> type.phases().stream().map(Phase::id).toList()).orElse(List.of());
        var perPhase = features(phases);
        var all = new ArrayList<Setting<?>>(List.of(gametype(), NAME, MIN_PLAYERS, MAX_PLAYERS, WAITING, LIVES, NAME_TAGS, messages(), TEAMS,
                TEAM_SPAWNS, RANDOM_SPAWNS, RESPAWN, RESPAWN_SECONDS, REGION, ARENAS, stats(), ADVANCEMENTS, QUIT, HudSettings.setting(phases),
                Setting.of(ROLES, Kind.each("role", () -> game.map(GameType::roles).orElse(List.of()), Kind.section(RoleSettings.role(perPhase))),
                        "the roles, handed out in this order").orElse(Map.of()),
                Setting.of(RANKS, Kind.each("rank", () -> game.map(GameType::ranks).orElse(List.of()), Kind.section(RoleSettings.rank(perPhase))),
                        "split each team at random, in this order").orElse(Map.of()),
                perPhase));
        game.ifPresent(type -> all.addAll(type.settings()));
        var variant = all.stream().filter(setting -> !setting.key().equals(GAMETYPE)).toList();
        all.add(Setting.of(GameLoader.VARIANTS, Kind.each("variant", List::of, Kind.section(variant)), "each played as its own choice, with only what differs")
                .orElse(Map.of()));
        all.add(ENABLED);
        return all;
    }

    /** Per phase, per feature: the feature's own settings. */
    private Setting<ConfigReader> features(List<String> phases) {
        Supplier<Map<String, Kind<?>>> byFeature = () -> {
            var kinds = new LinkedHashMap<String, Kind<?>>();
            features.ids().forEach(id -> kinds.put(id, Kind.section(features.find(id).orElseThrow().settings()).offering("{}", "false")));
            return kinds;
        };
        Supplier<Map<String, Kind<?>>> byPhase = () -> {
            var kinds = new LinkedHashMap<String, Kind<?>>();
            phases.forEach(phase -> kinds.put(phase, Kind.named(byFeature)));
            return kinds;
        };
        return Setting.of(FEATURES, Kind.named(byPhase), "per phase of the game type, per feature: its settings");
    }
}
