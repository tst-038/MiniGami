package io.github.tst_038.minigami.paper.info;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.gamenight.template.NightTemplate;
import io.github.tst_038.minigami.gamenight.template.PlaylistEdit;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.config.SettingsLoader;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.Variant;
import io.github.tst_038.minigami.paper.game.GameLoader;
import io.github.tst_038.minigami.paper.game.Role;
import io.github.tst_038.minigami.paper.schedule.Schedule;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Shows what is set up: lists of games, arenas, game types and features, and the details of one. Files are
 * shown as the last config load read them, problems included, so a file that failed to load can
 * still be looked at and fixed. Lines with a setting can be clicked to change it.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ConfigInfo {

    private record Files(YamlConfiguration config, SortedMap<String, YamlConfiguration> arenas,
            SortedMap<String, YamlConfiguration> games, SortedMap<String, YamlConfiguration> nights,
            SortedMap<String, YamlConfiguration> schedules, List<ConfigProblem> problems) {
    }

    /** A setting a game or feature declares; required ones have no default. Keys inside a setting have no description of their own. */
    private record SettingLine(String key, List<String> values, boolean required, String description) {
    }

    private final Messages messages;
    private final ConfigEditor editor;
    private final IdRegistry<GameType> gametypes;
    private final IdRegistry<FeatureFactory> features;
    private final Supplier<IdRegistry<Arena>> arenas;
    private final Supplier<IdRegistry<Game>> games;
    private final Supplier<IdRegistry<NightTemplate>> nights;
    private final Supplier<IdRegistry<Schedule>> schedules;
    private final Predicate<String> arenaInUse;
    private Files files = new Files(new YamlConfiguration(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), List.of());

    /**
     * @param arenas the arenas currently loaded
     * @param games the games currently loaded
     * @param nights the game night templates currently loaded
     * @param schedules the schedules currently loaded
     * @param arenaInUse whether a running match uses the arena with this id
     */
    public ConfigInfo(Messages messages, ConfigEditor editor, IdRegistry<GameType> gametypes, IdRegistry<FeatureFactory> features,
            Supplier<IdRegistry<Arena>> arenas, Supplier<IdRegistry<Game>> games,
            Supplier<IdRegistry<NightTemplate>> nights, Supplier<IdRegistry<Schedule>> schedules, Predicate<String> arenaInUse) {
        this.messages = messages;
        this.editor = editor;
        this.gametypes = gametypes;
        this.features = features;
        this.arenas = arenas;
        this.games = games;
        this.nights = nights;
        this.schedules = schedules;
        this.arenaInUse = arenaInUse;
    }

    /** Takes the files a config load read, valid or not, and every problem it found. */
    public void loaded(String settings, Map<String, YamlConfiguration> arenaFiles, Map<String, YamlConfiguration> gameFiles,
            Map<String, YamlConfiguration> nightFiles, Map<String, YamlConfiguration> scheduleFiles, List<ConfigProblem> problems) {
        var config = new YamlConfiguration();
        try {
            config.loadFromString(settings);
        } catch (InvalidConfigurationException e) {
            // Already one of the problems.
        }
        files = new Files(config, new TreeMap<>(arenaFiles), new TreeMap<>(gameFiles), new TreeMap<>(nightFiles), new TreeMap<>(scheduleFiles), List.copyOf(problems));
    }

    public void listGames(CommandSender sender) {
        var current = files;
        messages.send(sender, "info.games", Formatter.number("count", current.games().size()));
        current.games().forEach((id, config) -> {
            var loaded = games.get().find(id);
            var status = loaded.isPresent() && loaded.get().base().arenas().isEmpty() ? status("info.status.no-arenas", Placeholder.unparsed("id", id)) : null;
            messages.send(sender, "info.game-entry", Placeholder.unparsed("id", id),
                    Placeholder.unparsed("gametype", config.getString("gametype", "?")),
                    Formatter.number("variants", loaded.map(game -> game.variants().size()).orElse(0)),
                    Placeholder.component("status", config.getBoolean("enabled", true) ? status(Kind.GAME, id, loaded.isPresent(), status)
                            : status("info.status.disabled", Placeholder.unparsed("id", id))),
                    infoClick("/mga game " + id + " info"));
        });
    }

    public void listNights(CommandSender sender) {
        var current = files;
        messages.send(sender, "info.nights", Formatter.number("count", current.nights().size()));
        current.nights().forEach((id, config) -> {
            var loaded = nights.get().find(id);
            messages.send(sender, "info.night-entry", Placeholder.unparsed("id", id), Placeholder.parsed("name", config.getString("name", id)),
                    Formatter.number("games", config.getList(PlaylistEdit.KEY, List.of()).size()),
                    Placeholder.component("status", status(Kind.NIGHT, id, loaded.isPresent(), null)),
                    infoClick("/mga night info " + id));
        });
    }

    /** The night's settings, its numbered playlist, then what else can be set and any problems. */
    public void night(CommandSender sender, String id) {
        var config = files.nights().get(id);
        if (config == null) {
            unknownNight(sender, id);
            return;
        }
        messages.send(sender, "info.night", Placeholder.unparsed("id", id), Placeholder.parsed("name", config.getString("name", id)),
                Placeholder.component("status", status(Kind.NIGHT, id, nights.get().find(id).isPresent(), null)));
        var settings = YamlConfiguration.loadConfiguration(new StringReader(config.saveToString()));
        settings.set(PlaylistEdit.KEY, null);
        var set = "/mga night edit " + id + " set ";
        values(sender, settings, settings, set);
        nightPlaylist(sender, id);
        problems(sender, Kind.NIGHT, id);
    }

    /** The games of the night one per line, numbered as the playlist commands count them. */
    public void nightPlaylist(CommandSender sender, String id) {
        var config = files.nights().get(id);
        if (config == null) {
            unknownNight(sender, id);
            return;
        }
        var playlist = config.getList(PlaylistEdit.KEY, List.of());
        messages.send(sender, "info.night-playlist", Formatter.number("count", playlist.size()));
        for (int i = 0; i < playlist.size(); i++) {
            Map<?, ?> entry = playlist.get(i) instanceof Map<?, ?> map ? map : Map.of("game", playlist.get(i));
            var variant = entry.get("variant");
            var points = entry.get("points");
            messages.send(sender, "info.night-playlist-entry", Placeholder.unparsed("n", String.valueOf(i + 1)),
                    Placeholder.unparsed("game", String.valueOf(entry.get("game"))),
                    Placeholder.unparsed("variant", variant == null ? "" : " (" + variant + ")"),
                    Placeholder.unparsed("points", points == null ? "" : " " + show(points)));
        }
    }

    private void unknownNight(CommandSender sender, String id) {
        messages.send(sender, "night.unknown", Placeholder.unparsed("night", id), Placeholder.unparsed("nights", String.join(", ", files.nights().keySet())));
    }

    public void listSchedules(CommandSender sender) {
        var current = files;
        messages.send(sender, "info.schedules", Formatter.number("count", current.schedules().size()));
        current.schedules().forEach((id, config) -> messages.send(sender, "info.schedule-entry", Placeholder.unparsed("id", id),
                Placeholder.parsed("name", config.getString("name", id)), Placeholder.unparsed("when", config.getString("when", "?")),
                Formatter.number("games", config.getList("games", List.of()).size()),
                Placeholder.component("status", status(Kind.SCHEDULE, id, schedules.get().find(id).isPresent(), null)),
                infoClick("/mga schedule info " + id)));
    }

    /** The schedule's settings, games and rewards included, then what else can be set and any problems. */
    public void schedule(CommandSender sender, String id) {
        var config = files.schedules().get(id);
        if (config == null) {
            messages.send(sender, "schedule.unknown", Placeholder.unparsed("schedule", id), Placeholder.unparsed("schedules", String.join(", ", files.schedules().keySet())));
            return;
        }
        messages.send(sender, "info.schedule", Placeholder.unparsed("id", id), Placeholder.parsed("name", config.getString("name", id)),
                Placeholder.component("status", status(Kind.SCHEDULE, id, schedules.get().find(id).isPresent(), null)));
        var set = "/mga schedule edit " + id + " set ";
        values(sender, config, config, set);
        problems(sender, Kind.SCHEDULE, id);
    }

    public void listArenas(CommandSender sender) {
        var current = files;
        messages.send(sender, "info.arenas", Formatter.number("count", current.arenas().size()));
        current.arenas().forEach((id, config) -> {
            var spawns = config.getList("spawns", List.of()).size();
            var status = arenaInUse.test(id) ? status("info.status.in-use") : spawns == 0 ? status("info.status.no-spawns") : null;
            messages.send(sender, "info.arena-entry", Placeholder.unparsed("id", id),
                    Placeholder.unparsed("world", config.getString("world", "?")), Formatter.number("spawns", spawns),
                    Placeholder.component("status", status(Kind.ARENA, id, arenas.get().find(id).isPresent(), status)),
                    infoClick("/mga arena " + id + " info"));
        });
    }

    public void listGametypes(CommandSender sender) {
        messages.send(sender, "info.gametypes", Formatter.number("count", gametypes.ids().size()));
        gametypes.ids().forEach(id -> messages.send(sender, "info.gametype-entry", Placeholder.unparsed("id", id),
                Formatter.number("games", gamesOfType(id).size()), infoClick("/mga gametype " + id + " info")));
    }

    /** The game's settings, variants included, then what else can be set and any problems. */
    public void game(CommandSender sender, String id) {
        var current = files;
        var config = current.games().get(id);
        if (config == null) {
            messages.send(sender, "join.unknown-game", Placeholder.unparsed("game", id));
            return;
        }
        var game = games.get().find(id);
        var loaded = game.map(Game::base);
        messages.send(sender, "info.game", Placeholder.unparsed("id", id), Placeholder.parsed("name", config.getString("name", id)),
                Placeholder.unparsed("gametype", config.getString("gametype", "?")),
                Placeholder.component("status", status(Kind.GAME, id, game.isPresent(), null)));
        game.filter(found -> !found.variants().isEmpty()).ifPresent(found -> messages.send(sender, "info.game-variants",
                Placeholder.component("variants", Component.join(JoinConfiguration.commas(true), found.variants().values().stream()
                        .map(variant -> messages.render("info.game-variant", Placeholder.unparsed("id", variant.id().orElseThrow()),
                                Placeholder.parsed("name", variant.name())))
                        .toList()))));
        loaded.ifPresent(variant -> messages.send(sender, "info.game-arenas",
                Placeholder.unparsed("arenas", variant.arenas().isEmpty() ? "-" : variant.arenas().stream().map(Arena::id).collect(Collectors.joining(", ")))));
        loaded.flatMap(Variant::teams).ifPresent(teams -> {
            if (teams.count() > 0) {
                messages.send(sender, "info.game-teams-count", Formatter.number("count", teams.count()));
            } else {
                messages.send(sender, "info.game-teams-size", Formatter.number("size", teams.size()));
            }
        });
        loaded.map(Variant::roles).filter(roles -> !roles.isEmpty()).ifPresent(roles -> messages.send(sender, "info.game-roles",
                Placeholder.component("roles", Component.join(JoinConfiguration.commas(true), roles.stream().map(Role::displayName).toList()))));
        values(sender, config, config, "/mga game " + id + " set ");
        problems(sender, Kind.GAME, id);
    }

    public void gametype(CommandSender sender, String id) {
        var found = gametypes.find(id);
        if (found.isEmpty()) {
            messages.send(sender, "info.unknown-gametype", Placeholder.unparsed("id", id));
            return;
        }
        var game = found.get();
        messages.send(sender, "info.gametype", Placeholder.unparsed("id", id),
                Placeholder.unparsed("win-condition", game.winCondition().getClass().getSimpleName()), Formatter.number("lives", game.lives()));
        for (var phase : game.phases()) {
            if (phase.isTimed()) {
                messages.send(sender, "info.phase-timed", Placeholder.unparsed("phase", phase.id()),
                        Formatter.number("seconds", phase.durationTicks() / 20.0));
            } else {
                messages.send(sender, "info.phase-untimed", Placeholder.unparsed("phase", phase.id()));
            }
        }
        if (!game.roles().isEmpty()) {
            messages.send(sender, "info.gametype-roles", Placeholder.unparsed("roles", String.join(", ", game.roles())));
        }
        if (!game.regions().isEmpty()) {
            messages.send(sender, "info.gametype-regions", Placeholder.unparsed("regions", String.join(", ", game.regions())));
        }
        game.regionPerTeam().ifPresent(region -> messages.send(sender, "info.gametype-region-per-team", Placeholder.unparsed("region", region)));
        game.regionGroups().forEach(group -> messages.send(sender, "info.gametype-region-group", Placeholder.unparsed("group", group)));
        settings(sender, declared(game.settings()));
        usedBy(sender, gamesOfType(id));
    }

    /**
     * Every option a game file can have, grouped by the section it lives in ({@code effect.<effect>} and so on), then what each
     * feature takes per phase. A game type adds its own options.
     */
    public void options(CommandSender sender, Optional<String> gametype) {
        if (gametype.isPresent() && gametypes.find(gametype.get()).isEmpty()) {
            messages.send(sender, "info.unknown-gametype", Placeholder.unparsed("id", gametype.get()));
            return;
        }
        messages.send(sender, "info.options", Placeholder.unparsed("gametype", gametype.orElse("-")));
        // Variants take the same keys, and features are shown once below instead of under every phase and role.
        grouped(sender, declared(editor.settings(gametype.orElse(""))).stream()
                .filter(line -> !line.key().startsWith("variants.") && !line.key().startsWith("features.") && !line.key().contains(".features."))
                .toList());
        for (var id : features.ids()) {
            messages.send(sender, "info.options-feature", Placeholder.unparsed("id", id));
            grouped(sender, declared(features.find(id).orElseThrow().settings()));
        }
    }

    public void config(CommandSender sender) {
        var current = files;
        messages.send(sender, "info.config", Placeholder.unparsed("file", SettingsLoader.FILE));
        values(sender, current.config(), current.config(), "/mga config set ");
        problems(sender, Kind.CONFIG, "");
    }

    /** Leaf values one per line; a section of only plain values, such as a location, on one line. */
    private void values(CommandSender sender, ConfigurationSection config, ConfigurationSection own, String set) {
        for (String key : config.getKeys(false)) {
            var path = config.getCurrentPath().isEmpty() ? key : config.getCurrentPath() + "." + key;
            var value = config.get(key);
            if (value instanceof ConfigurationSection section && section.getValues(false).values().stream().anyMatch(ConfigurationSection.class::isInstance)) {
                values(sender, section, own, set);
                continue;
            }
            var shown = show(value);
            messages.send(sender, own.contains(path) ? "info.value" : "info.value-inherited", Placeholder.unparsed("key", path),
                    Placeholder.unparsed("value", shown), Placeholder.styling("set", ClickEvent.suggestCommand(set + path + " " + shown)));
        }
    }

    private void problems(CommandSender sender, Kind kind, String id) {
        var file = kind.file(id);
        files.problems().stream().filter(problem -> problem.file().equals(file))
                .forEach(problem -> messages.send(sender, "reload.problem", Placeholder.unparsed("problem", problem.toString())));
    }

    private void settings(CommandSender sender, List<SettingLine> settings) {
        if (settings.isEmpty()) {
            messages.send(sender, "info.no-settings");
        }
        for (var setting : settings) {
            var values = setting.values().isEmpty() ? messages.render("info.any-value") : Component.text(String.join(" | ", setting.values()));
            messages.send(sender, setting.required() ? "info.setting-required" : "info.setting", Placeholder.unparsed("key", setting.key()),
                    Placeholder.component("values", values), Placeholder.unparsed("description", setting.description()));
        }
    }

    /** Settings one per line; those inside a section such as {@code effect.<effect>} follow its heading, with the key relative to it. */
    private void grouped(CommandSender sender, List<SettingLine> lines) {
        var groups = new TreeMap<String, List<SettingLine>>();
        for (var line : lines) {
            var dot = line.key().lastIndexOf('.');
            groups.computeIfAbsent(dot < 0 ? "" : line.key().substring(0, dot), group -> new ArrayList<>())
                    .add(new SettingLine(line.key().substring(dot + 1), line.values(), line.required(), line.description()));
        }
        groups.forEach((group, members) -> {
            if (!group.isEmpty()) {
                messages.send(sender, "info.options-group", Placeholder.unparsed("group", group));
            }
            settings(sender, members);
        });
    }

    /** @param uses a game id, or a game id and what it uses there; each clickable to that game's info */
    private void usedBy(CommandSender sender, List<String[]> uses) {
        if (uses.isEmpty()) {
            messages.send(sender, "info.used-by-none");
            return;
        }
        var gameLinks = uses.stream().map(use -> Component.text(String.join(" ", use))
                .clickEvent(ClickEvent.runCommand("/mga game " + use[0] + " info"))).toList();
        messages.send(sender, "info.used-by", Placeholder.component("games", Component.join(JoinConfiguration.commas(true), gameLinks)));
    }

    private List<String[]> gamesOfType(String gametype) {
        var uses = new ArrayList<String[]>();
        files.games().forEach((id, config) -> {
            if (gametype.equals(config.getString("gametype"))) {
                uses.add(new String[] {id});
            }
        });
        return uses;
    }

    /** The status of a file: its problems first, then whether it is loaded, then the given status, else fine. */
    private Component status(Kind kind, String id, boolean loaded, Component status) {
        var file = kind.file(id);
        var problems = files.problems().stream().filter(problem -> problem.file().equals(file)).count();
        if (problems > 0) {
            return messages.render("info.status.problems", Formatter.number("problems", problems));
        }
        if (!loaded) {
            return status("info.status.not-loaded");
        }
        return status != null ? status : status("info.status.ok");
    }

    private Component status(String key, TagResolver... placeholders) {
        return messages.render(key, placeholders);
    }

    private static TagResolver infoClick(String command) {
        return Placeholder.styling("info", ClickEvent.runCommand(command));
    }

    /** The keys a game or feature declares, dynamic ones as patterns such as {@code <block>}. */
    private static List<SettingLine> declared(List<Setting<?>> settings) {
        return Setting.keys(settings, false).entrySet().stream().map(entry -> {
            var top = settings.stream().filter(setting -> setting.key().equals(entry.getKey())).findFirst();
            return new SettingLine(entry.getKey(), entry.getValue(), top.map(Setting::required).orElse(false),
                    top.map(Setting::description).orElse(""));
        }).toList();
    }

    /** A value as YAML would write it inline, so it can be pasted back into {@code set}. */
    static String show(Object value) {
        return switch (value) {
            case ConfigurationSection section -> show(section.getValues(false));
            case Map<?, ?> map -> map.entrySet().stream().map(entry -> entry.getKey() + ": " + show(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
            case List<?> list -> list.stream().map(ConfigInfo::show).collect(Collectors.joining(", ", "[", "]"));
            case Double number -> String.valueOf(Math.round(number * 100) / 100.0);
            case null -> "";
            default -> value.toString();
        };
    }
}
