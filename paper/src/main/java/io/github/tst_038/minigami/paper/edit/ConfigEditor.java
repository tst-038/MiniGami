package io.github.tst_038.minigami.paper.edit;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaLoader;
import io.github.tst_038.minigami.paper.config.ConfigFiles;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import io.github.tst_038.minigami.paper.config.Settings;
import io.github.tst_038.minigami.paper.config.SettingsLoader;
import io.github.tst_038.minigami.paper.mode.ModeLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Edits any setting of a mode, an arena or {@code config.yml} by command. The edited file is checked
 * by the loader that loads it and only written when it has no problems, after which the config is
 * loaded again. Keys complete from what the loaders asked for, so new settings need no code here.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ConfigEditor {

    /** The files that can be edited. */
    public enum Kind {
        CONFIG, ARENA, MODE;

        String file(String id) {
            return switch (this) {
                case CONFIG -> SettingsLoader.FILE;
                case ARENA -> "arenas/" + id + ".yml";
                case MODE -> "modes/" + id + ".yml";
            };
        }
    }

    /** Taken by {@code /mg mode create}, so a mode with this id couldn't be edited. */
    private static final String RESERVED_ID = "create";
    private static final long CHECK_TIMEOUT_SECONDS = 10;

    private final Plugin plugin;
    private final Messages messages;
    private final IdRegistry<GameType> games;
    private final ModeLoader modeLoader;
    private final Supplier<IdRegistry<Arena>> arenas;
    private final Supplier<Settings> settings;
    private final Predicate<String> arenaInUse;
    private final Consumer<Audience> reload;
    private volatile Keys keys = new Keys(Collections.emptySortedMap(), Map.of(), Map.of());

    private record Keys(SortedMap<String, List<String>> config, Map<String, SortedMap<String, List<String>>> arenas,
            Map<String, SortedMap<String, List<String>>> modes) {
    }

    /**
     * @param arenas the arenas currently loaded, which modes are checked against
     * @param settings the settings currently loaded, for whether modes may wait in the lobby
     * @param arenaInUse whether a running match uses the arena with this id; such an arena can't be changed
     * @param reload loads the config again, off the server thread, and tells this audience how that went
     */
    public ConfigEditor(Plugin plugin, Messages messages, IdRegistry<GameType> games, IdRegistry<FeatureFactory> features,
            Supplier<IdRegistry<Arena>> arenas, Supplier<Settings> settings, Predicate<String> arenaInUse, Consumer<Audience> reload) {
        this.plugin = plugin;
        this.messages = messages;
        this.games = games;
        this.modeLoader = new ModeLoader(games, features);
        this.arenas = arenas;
        this.settings = settings;
        this.arenaInUse = arenaInUse;
        this.reload = reload;
    }

    /** Takes the keys of every file from a config load, valid files or not, for completion. Any thread. */
    public void keysLoaded(SortedMap<String, List<String>> config, Map<String, SortedMap<String, List<String>>> arenas,
            Map<String, SortedMap<String, List<String>>> modes) {
        keys = new Keys(config, Map.copyOf(arenas), Map.copyOf(modes));
    }

    /** Ids of the files in the folder, including those that failed to load. Any thread. */
    public Collection<String> ids(Kind kind) {
        return switch (kind) {
            case CONFIG -> List.of();
            case ARENA -> keys.arenas().keySet().stream().sorted().toList();
            case MODE -> keys.modes().keySet().stream().sorted().toList();
        };
    }

    /** The keys a file has or its loader asked for. Any thread. */
    public Collection<String> keys(Kind kind, String id) {
        return keysOf(kind, id).keySet();
    }

    /** Values that make sense for the key, such as {@code true} and {@code false}; none if anything goes. Any thread. */
    public List<String> values(Kind kind, String id, String key) {
        return keysOf(kind, id).getOrDefault(key, List.of());
    }

    private SortedMap<String, List<String>> keysOf(Kind kind, String id) {
        var current = keys;
        return switch (kind) {
            case CONFIG -> current.config();
            case ARENA -> current.arenas().getOrDefault(id, Collections.emptySortedMap());
            case MODE -> current.modes().getOrDefault(id, Collections.emptySortedMap());
        };
    }

    public Collection<String> gameIds() {
        return games.ids();
    }

    /**
     * @param id ignored for {@link Kind#CONFIG}
     * @param text a YAML value such as {@code 5}, {@code [a, b]} or {@code { y: 0 }}, or {@value #ConfigLocations.HERE}
     */
    public void set(CommandSender sender, Kind kind, String id, String key, String text) {
        Consumer<YamlConfiguration> change;
        if (text.equals(ConfigLocations.HERE)) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "edit.here-needs-player");
                return;
            }
            var location = player.getLocation();
            change = config -> config.set(key, location(config, location));
        } else {
            var value = parse(text);
            if (value == null) {
                messages.send(sender, "edit.bad-value", Placeholder.unparsed("value", text));
                return;
            }
            change = config -> config.set(key, value);
        }
        edit(sender, kind, id, false, change, "edit.set", Placeholder.unparsed("key", key));
    }

    public void unset(CommandSender sender, Kind kind, String id, String key) {
        edit(sender, kind, id, false, config -> {
            if (!config.contains(key)) {
                throw new IllegalArgumentException("has no key " + key);
            }
            config.set(key, null);
        }, "edit.unset", Placeholder.unparsed("key", key));
    }

    /** A mode that waits in the arena, for 2 to 8 players; change the rest with {@link #set}. */
    public void createMode(CommandSender sender, String id, String game, String arena) {
        edit(sender, Kind.MODE, id, true, config -> {
            config.set("game", game);
            config.set("min-players", 2);
            config.set("max-players", 8);
            config.set("waiting", "arena");
            config.set("arenas", List.of(arena));
        }, "edit.created");
    }

    /** Arenas and modes only; the file is kept as {@code <file>.deleted}. */
    public void delete(CommandSender sender, Kind kind, String id) {
        var file = kind.file(id);
        if (!editable(sender, kind, id)) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            var fileTag = Placeholder.unparsed("file", file);
            try {
                if (!ConfigFiles.delete(plugin.getDataPath().resolve(file))) {
                    messages.send(sender, "edit.unknown", fileTag);
                    return;
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete " + file, e);
                messages.send(sender, "edit.save-failed", fileTag);
                return;
            }
            plugin.getLogger().info(sender.getName() + " deleted " + file);
            messages.send(sender, "edit.deleted", fileTag);
            reload.accept(sender);
        });
    }

    private boolean editable(CommandSender sender, Kind kind, String id) {
        if (kind == Kind.CONFIG) {
            return true;
        }
        if (!IdRegistry.isValid(id) || id.equals(RESERVED_ID)) {
            messages.send(sender, "edit.invalid-id", Placeholder.unparsed("id", id));
            return false;
        }
        if (kind == Kind.ARENA && arenaInUse.test(id)) {
            messages.send(sender, "arena.in-use", Placeholder.unparsed("arena", id));
            return false;
        }
        return true;
    }

    /** Changes the file off the server thread, checks it on the server thread, and only then writes it. */
    private void edit(CommandSender sender, Kind kind, String id, boolean create, Consumer<YamlConfiguration> change,
            String done, TagResolver... placeholders) {
        if (!editable(sender, kind, id)) {
            return;
        }
        var file = kind.file(id);
        var fileTag = Placeholder.unparsed("file", file);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            var path = plugin.getDataPath().resolve(file);
            if (create == Files.exists(path)) {
                messages.send(sender, create ? "edit.exists" : "edit.unknown", fileTag);
                return;
            }
            List<ConfigProblem> problems;
            try {
                problems = ConfigFiles.update(path, change, config -> {
                    // Other modes too, for the ones it extends; read here, off the server thread.
                    var modes = kind == Kind.MODE ? YamlFiles.read(path.getParent()).files() : Map.<String, YamlConfiguration>of();
                    return onServerThread(() -> check(kind, id, config, modes));
                });
            } catch (IllegalArgumentException e) {
                problems = List.of(new ConfigProblem(file, "", e.getMessage()));
            } catch (IOException | InvalidConfigurationException | IllegalStateException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write " + file, e);
                messages.send(sender, "edit.save-failed", fileTag);
                return;
            }
            if (!problems.isEmpty()) {
                messages.send(sender, "edit.invalid", fileTag);
                problems.forEach(problem -> messages.send(sender, "edit.problem", Placeholder.unparsed("problem", problem.toString())));
                return;
            }
            plugin.getLogger().info(sender.getName() + " changed " + file);
            messages.send(sender, done, TagResolver.resolver(TagResolver.resolver(placeholders), fileTag));
            reload.accept(sender);
        });
    }

    /**
     * The problems the file's own loader finds in it. Looks up worlds, so runs on the server thread.
     *
     * @param modes the saved modes, which a mode may extend
     */
    private List<ConfigProblem> check(Kind kind, String id, YamlConfiguration config, Map<String, YamlConfiguration> modes) {
        var worlds = plugin.getServer();
        return switch (kind) {
            case CONFIG -> SettingsLoader.load(config.saveToString(), worlds::getWorld).problems();
            case ARENA -> ArenaLoader.load(new YamlFiles("arenas", Map.of(id, config), List.of()), worlds::getWorld).problems();
            case MODE -> {
                var files = new HashMap<>(modes);
                files.put(id, config);
                yield modeLoader.load(new YamlFiles("modes", files, List.of()), arenas.get(), settings.get().lobby().isPresent())
                        .problems().stream().filter(problem -> problem.file().equals(kind.file(id))).toList();
            }
        };
    }

    /** Blocks until the task ran on the server thread; never call it from there. */
    private <T> T onServerThread(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, runnable -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, runnable))
                    .get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking the file", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not check the file on the server thread", e);
        }
    }

    /**
     * A location for this file: files with a {@code world} key, such as arenas, keep their locations
     * in that world; others, such as {@code config.yml}, name the world in each location.
     */
    static Map<String, Object> location(ConfigurationSection config, Location location) {
        var world = config.getString("world");
        if (world == null) {
            return ConfigLocations.writeWithWorld(location);
        }
        if (!world.equals(location.getWorld().getName())) {
            throw new IllegalArgumentException("you are in world " + location.getWorld().getName() + ", but its locations are in " + world);
        }
        return ConfigLocations.write(location);
    }

    /** The value a command typed, read as YAML; {@code null} if it isn't one. */
    static Object parse(String text) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString("value: " + text);
        } catch (InvalidConfigurationException e) {
            return null;
        }
        if (yaml.getKeys(false).size() != 1) {
            return null;
        }
        var value = yaml.get("value");
        return value instanceof ConfigurationSection section ? plain(section) : value;
    }

    private static Map<String, Object> plain(ConfigurationSection section) {
        var map = new LinkedHashMap<String, Object>();
        section.getValues(false).forEach((key, value) -> map.put(key, value instanceof ConfigurationSection child ? plain(child) : value));
        return map;
    }
}
