package io.github.tst_038.minigami.paper.info;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.config.SettingsLoader;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.mode.Mode;
import io.github.tst_038.minigami.paper.mode.ModeLoader;
import io.github.tst_038.minigami.paper.mode.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
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
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Shows what is set up: lists of modes, arenas, games and features, and the details of one. Files are
 * shown as the last config load read them, problems included, so a file that failed to load can
 * still be looked at and fixed. Lines with a setting can be clicked to change it.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ConfigInfo {

    private record Files(YamlConfiguration config, SortedMap<String, YamlConfiguration> arenas,
            SortedMap<String, YamlConfiguration> modes, SortedMap<String, YamlConfiguration> inherited, List<ConfigProblem> problems) {
    }

    /** A setting a game or feature reads; required ones have no default. */
    private record Setting(String key, List<String> values, boolean required) {
    }

    private final Messages messages;
    private final ConfigEditor editor;
    private final IdRegistry<GameType> games;
    private final IdRegistry<FeatureFactory> features;
    private final Supplier<IdRegistry<Arena>> arenas;
    private final Supplier<IdRegistry<Mode>> modes;
    private final Predicate<String> arenaInUse;
    private Files files = new Files(new YamlConfiguration(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), List.of());

    /**
     * @param arenas the arenas currently loaded
     * @param modes the modes currently loaded
     * @param arenaInUse whether a running match uses the arena with this id
     */
    public ConfigInfo(Messages messages, ConfigEditor editor, IdRegistry<GameType> games, IdRegistry<FeatureFactory> features,
            Supplier<IdRegistry<Arena>> arenas, Supplier<IdRegistry<Mode>> modes, Predicate<String> arenaInUse) {
        this.messages = messages;
        this.editor = editor;
        this.games = games;
        this.features = features;
        this.arenas = arenas;
        this.modes = modes;
        this.arenaInUse = arenaInUse;
    }

    /** Takes the files a config load read, valid or not, and every problem it found. */
    public void loaded(String settings, Map<String, YamlConfiguration> arenaFiles, Map<String, YamlConfiguration> modeFiles,
            List<ConfigProblem> problems) {
        var config = new YamlConfiguration();
        try {
            config.loadFromString(settings);
        } catch (InvalidConfigurationException e) {
            // Already one of the problems.
        }
        var inherited = new TreeMap<String, YamlConfiguration>();
        modeFiles.keySet().forEach(id -> inherited.put(id, ModeLoader.inherited(id, modeFiles)));
        files = new Files(config, new TreeMap<>(arenaFiles), new TreeMap<>(modeFiles), inherited, List.copyOf(problems));
    }

    public void listModes(CommandSender sender) {
        var current = files;
        messages.send(sender, "info.modes", Formatter.number("count", current.modes().size()));
        current.inherited().forEach((id, config) -> {
            var loaded = modes.get().find(id);
            var status = loaded.isPresent() && loaded.get().arenas().isEmpty() ? status("info.status.no-arenas") : null;
            messages.send(sender, "info.mode-entry", Placeholder.unparsed("id", id),
                    Placeholder.unparsed("game", config.getString("gametype", "?")),
                    Placeholder.component("status", status(Kind.MODE, id, loaded.isPresent(), status)),
                    infoClick("/mg mode " + id + " info"));
        });
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
                    infoClick("/mg arena " + id + " info"));
        });
    }

    public void listGames(CommandSender sender) {
        messages.send(sender, "info.games", Formatter.number("count", games.ids().size()));
        games.ids().forEach(id -> messages.send(sender, "info.game-entry", Placeholder.unparsed("id", id),
                Formatter.number("modes", modesWithGame(id).size()), infoClick("/mg gametype " + id + " info")));
    }

    public void listFeatures(CommandSender sender) {
        messages.send(sender, "info.features", Formatter.number("count", features.ids().size()));
        features.ids().forEach(id -> messages.send(sender, "info.feature-entry", Placeholder.unparsed("id", id),
                Formatter.number("modes", usesOfFeature(id).size()), infoClick("/mg feature " + id + " info")));
    }

    /** The settings after inheritance, marking inherited ones, then what else can be set and any problems. */
    public void mode(CommandSender sender, String id) {
        var current = files;
        var own = current.modes().get(id);
        if (own == null) {
            messages.send(sender, "join.unknown-mode", Placeholder.unparsed("mode", id));
            return;
        }
        var config = current.inherited().get(id);
        var loaded = modes.get().find(id);
        messages.send(sender, "info.mode", Placeholder.unparsed("id", id), Placeholder.parsed("name", config.getString("name", id)),
                Placeholder.unparsed("game", config.getString("gametype", "?")),
                Placeholder.component("status", status(Kind.MODE, id, loaded.isPresent(), null)));
        if (own.contains("extends")) {
            messages.send(sender, "info.mode-parent", Placeholder.unparsed("parent", own.getString("extends", "")),
                    infoClick("/mg mode " + own.getString("extends") + " info"));
        }
        loaded.ifPresent(mode -> messages.send(sender, "info.mode-arenas",
                Placeholder.unparsed("arenas", mode.arenas().isEmpty() ? "-" : mode.arenas().stream().map(Arena::id).collect(Collectors.joining(", ")))));
        loaded.flatMap(Mode::teams).ifPresent(teams -> {
            if (teams.count() > 0) {
                messages.send(sender, "info.mode-teams-count", Formatter.number("count", teams.count()));
            } else {
                messages.send(sender, "info.mode-teams-size", Formatter.number("size", teams.size()));
            }
        });
        loaded.map(Mode::roles).filter(roles -> !roles.isEmpty()).ifPresent(roles -> messages.send(sender, "info.mode-roles",
                Placeholder.component("roles", Component.join(JoinConfiguration.commas(true), roles.stream().map(Role::displayName).toList()))));
        values(sender, config, own, "/mg mode " + id + " set ");
        unset(sender, editor.keys(Kind.MODE, id), config, "/mg mode " + id + " set ");
        problems(sender, Kind.MODE, id);
    }

    public void game(CommandSender sender, String id) {
        var found = games.find(id);
        if (found.isEmpty()) {
            messages.send(sender, "info.unknown-game", Placeholder.unparsed("id", id));
            return;
        }
        var game = found.get();
        messages.send(sender, "info.game", Placeholder.unparsed("id", id),
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
            messages.send(sender, "info.game-roles", Placeholder.unparsed("roles", String.join(", ", game.roles())));
        }
        if (!game.regions().isEmpty()) {
            messages.send(sender, "info.game-regions", Placeholder.unparsed("regions", String.join(", ", game.regions())));
        }
        game.regionPerTeam().ifPresent(region -> messages.send(sender, "info.game-region-per-team", Placeholder.unparsed("region", region)));
        settings(sender, settingsRead(game::configure));
        usedBy(sender, modesWithGame(id));
    }

    public void feature(CommandSender sender, String id) {
        var factory = features.find(id);
        if (factory.isEmpty()) {
            messages.send(sender, "info.unknown-feature", Placeholder.unparsed("id", id));
            return;
        }
        messages.send(sender, "info.feature", Placeholder.unparsed("id", id));
        settings(sender, settingsRead(factory.get()::create));
        usedBy(sender, usesOfFeature(id));
    }

    public void config(CommandSender sender) {
        var current = files;
        messages.send(sender, "info.config", Placeholder.unparsed("file", SettingsLoader.FILE));
        values(sender, current.config(), current.config(), "/mg config set ");
        unset(sender, editor.keys(Kind.CONFIG, ""), current.config(), "/mg config set ");
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

    /** The keys the loader asked for that aren't set, each clickable to set it. */
    private void unset(CommandSender sender, Collection<String> keys, ConfigurationSection config, String set) {
        var missing = keys.stream().filter(key -> !config.contains(key))
                .map(key -> Component.text(key).clickEvent(ClickEvent.suggestCommand(set + key + " "))).toList();
        if (!missing.isEmpty()) {
            messages.send(sender, "info.unset", Placeholder.component("keys", Component.join(JoinConfiguration.commas(true), missing)));
        }
    }

    private void problems(CommandSender sender, Kind kind, String id) {
        var file = kind.file(id);
        files.problems().stream().filter(problem -> problem.file().equals(file))
                .forEach(problem -> messages.send(sender, "reload.problem", Placeholder.unparsed("problem", problem.toString())));
    }

    private void settings(CommandSender sender, List<Setting> settings) {
        if (settings.isEmpty()) {
            messages.send(sender, "info.no-settings");
        }
        for (var setting : settings) {
            var values = setting.values().isEmpty() ? messages.render("info.any-value") : Component.text(String.join(" | ", setting.values()));
            messages.send(sender, setting.required() ? "info.setting-required" : "info.setting", Placeholder.unparsed("key", setting.key()),
                    Placeholder.component("values", values));
        }
    }

    /** @param uses a mode id, or a mode id and what it uses there; each clickable to that mode's info */
    private void usedBy(CommandSender sender, List<String[]> uses) {
        if (uses.isEmpty()) {
            messages.send(sender, "info.used-by-none");
            return;
        }
        var modeLinks = uses.stream().map(use -> Component.text(String.join(" ", use))
                .clickEvent(ClickEvent.runCommand("/mg mode " + use[0] + " info"))).toList();
        messages.send(sender, "info.used-by", Placeholder.component("modes", Component.join(JoinConfiguration.commas(true), modeLinks)));
    }

    private List<String[]> modesWithGame(String game) {
        var uses = new ArrayList<String[]>();
        files.inherited().forEach((id, config) -> {
            if (game.equals(config.getString("gametype"))) {
                uses.add(new String[] {id});
            }
        });
        return uses;
    }

    /** Per mode, the phases it turns the feature on in. */
    private List<String[]> usesOfFeature(String feature) {
        var uses = new ArrayList<String[]>();
        files.inherited().forEach((id, config) -> {
            var phases = config.getConfigurationSection("features");
            if (phases == null) {
                return;
            }
            for (String phase : phases.getKeys(false)) {
                var value = phases.get(phase + "." + feature);
                if (value != null && !Boolean.FALSE.equals(value)) {
                    uses.add(new String[] {id, "(" + phase + ")"});
                }
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

    private Component status(String key) {
        return messages.render(key);
    }

    private static TagResolver infoClick(String command) {
        return Placeholder.styling("info", ClickEvent.runCommand(command));
    }

    /**
     * What a game or feature reads, found by letting it read an empty section: every key it asks for,
     * and as required the ones it reports missing. One that can't be made without settings shows none.
     */
    private static List<Setting> settingsRead(Consumer<ConfigReader> read) {
        var reader = ConfigReader.of("", new MemoryConfiguration());
        try {
            read.accept(reader);
        } catch (RuntimeException e) {
            return List.of();
        }
        var required = new HashSet<String>();
        try {
            reader.check();
        } catch (InvalidConfigException e) {
            e.problems().forEach(problem -> required.add(problem.key()));
        }
        return new TreeMap<>(reader.askedKeys()).entrySet().stream()
                .map(entry -> new Setting(entry.getKey(), entry.getValue(), required.contains(entry.getKey()))).toList();
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
