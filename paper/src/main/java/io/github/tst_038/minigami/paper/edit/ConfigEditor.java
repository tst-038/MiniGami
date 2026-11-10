package io.github.tst_038.minigami.paper.edit;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaFile;
import io.github.tst_038.minigami.paper.arena.ArenaLoader;
import io.github.tst_038.minigami.paper.config.ConfigFiles;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import io.github.tst_038.minigami.paper.config.Settings;
import io.github.tst_038.minigami.paper.config.SettingsLoader;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.paper.game.GameLoader;
import io.github.tst_038.minigami.paper.night.NightTemplateLoader;
import io.github.tst_038.minigami.paper.schedule.ScheduleLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
 * Edits any setting of a game, an arena, a game night, a schedule or {@code config.yml} by command. The edited file is checked
 * by the loader that loads it and only written when it has no problems, after which the config is
 * loaded again. Keys complete from what the loaders asked for, so new settings need no code here.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ConfigEditor {

    /** The files that can be edited. */
    public enum Kind {
        CONFIG, ARENA, GAME, NIGHT, SCHEDULE;

        /** The file, relative to the plugin folder, such as {@code games/spleef.yml}; the id is ignored for {@link #CONFIG}. */
        public String file(String id) {
            return switch (this) {
                case CONFIG -> SettingsLoader.FILE;
                case ARENA -> "arenas/" + ArenaLoader.LAYOUT.formatted(id);
                case GAME -> "games/" + id + ".yml";
                case NIGHT -> NightTemplateLoader.FOLDER + "/" + id + ".yml";
                case SCHEDULE -> ScheduleLoader.FOLDER + "/" + id + ".yml";
            };
        }
    }

    /** Taken by commands such as {@code /mga game list}, so an arena, game or game night with one of these ids couldn't be reached. */
    private static final Set<String> RESERVED_IDS = Set.of("create", "list");
    private static final long CHECK_TIMEOUT_SECONDS = 10;

    private final Plugin plugin;
    private final Messages messages;
    private final IdRegistry<GameType> gametypes;
    private final GameLoader gameLoader;
    private final Supplier<IdRegistry<Game>> games;
    private final Supplier<IdRegistry<Arena>> arenas;
    private final Supplier<Settings> settings;
    private final Predicate<String> arenaInUse;
    private final Consumer<Audience> reload;
    private volatile Keys keys = new Keys(Collections.emptySortedMap(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    /**
     * @param games the keys each game file has or its loader asked for
     * @param gametypes each game's game type, for the keys it can have
     * @param declared per game, its keys together with every key it can have; made when first completed
     */
    private record Keys(SortedMap<String, List<String>> config, Map<String, SortedMap<String, List<String>>> arenas,
            Map<String, SortedMap<String, List<String>>> games, Map<String, String> gametypes, Map<String, SortedMap<String, List<String>>> nights,
            Map<String, SortedMap<String, List<String>>> schedules, Map<String, SortedMap<String, List<String>>> declared) {

        Keys(SortedMap<String, List<String>> config, Map<String, SortedMap<String, List<String>>> arenas,
                Map<String, SortedMap<String, List<String>>> games, Map<String, String> gametypes, Map<String, SortedMap<String, List<String>>> nights,
                Map<String, SortedMap<String, List<String>>> schedules) {
            this(config, Map.copyOf(arenas), Map.copyOf(games), Map.copyOf(gametypes), Map.copyOf(nights), Map.copyOf(schedules), new ConcurrentHashMap<>());
        }
    }

    /**
     * @param arenas the arenas currently loaded, which games are checked against
     * @param games the games currently loaded, which game nights are checked against
     * @param settings the settings currently loaded, for whether games may wait in the lobby
     * @param arenaInUse whether a running match uses the arena with this id; such an arena can't be changed
     * @param reload loads the config again, off the server thread, and tells this audience how that went
     */
    public ConfigEditor(Plugin plugin, Messages messages, IdRegistry<GameType> gametypes, GameLoader gameLoader,
            Supplier<IdRegistry<Arena>> arenas, Supplier<IdRegistry<Game>> games, Supplier<Settings> settings, Predicate<String> arenaInUse, Consumer<Audience> reload) {
        this.plugin = plugin;
        this.messages = messages;
        this.gametypes = gametypes;
        this.gameLoader = gameLoader;
        this.arenas = arenas;
        this.games = games;
        this.settings = settings;
        this.arenaInUse = arenaInUse;
        this.reload = reload;
    }

    /**
     * Takes the keys of every file from a config load, valid files or not, for completion. Any thread.
     *
     * @param gametypes each game's game type as its file names it, empty when it names none
     */
    public void keysLoaded(SortedMap<String, List<String>> config, Map<String, SortedMap<String, List<String>>> arenas,
            Map<String, SortedMap<String, List<String>>> games, Map<String, String> gametypes, Map<String, SortedMap<String, List<String>>> nights,
            Map<String, SortedMap<String, List<String>>> schedules) {
        keys = new Keys(config, arenas, games, gametypes, nights, schedules);
    }

    /** Ids of the files in the folder, including those that failed to load. Any thread. */
    public Collection<String> ids(Kind kind) {
        return switch (kind) {
            case CONFIG -> List.of();
            case ARENA -> keys.arenas().keySet().stream().sorted().toList();
            case GAME -> keys.games().keySet().stream().sorted().toList();
            case NIGHT -> keys.nights().keySet().stream().sorted().toList();
            case SCHEDULE -> keys.schedules().keySet().stream().sorted().toList();
        };
    }

    /** The keys a file has or its loader asked for. Any thread. */
    public Collection<String> keys(Kind kind, String id) {
        return keysOf(kind, id).keySet();
    }

    /** Every setting a game file of the game type can have; see {@link GameLoader#settings}. */
    public List<Setting<?>> settings(String gametype) {
        return gameLoader.settings(gametype);
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
            case GAME -> current.games().containsKey(id) ? current.declared().computeIfAbsent(id, game -> {
                var all = new TreeMap<>(current.games().get(game));
                all.putAll(gameLoader.keys(current.gametypes().getOrDefault(game, "")));
                return Collections.unmodifiableSortedMap(all);
            }) : Collections.emptySortedMap();
            case NIGHT -> current.nights().getOrDefault(id, Collections.emptySortedMap());
            case SCHEDULE -> current.schedules().getOrDefault(id, Collections.emptySortedMap());
        };
    }

    /** The variants in the game's file, valid or not. Any thread. */
    public List<String> variantIds(String game) {
        return variantIds(keys(Kind.GAME, game));
    }

    /** The keys a variant of the game can set: the game's own, but not its game type or variants. Any thread. */
    public List<String> variantKeys(String game) {
        return variantKeys(keys(Kind.GAME, game));
    }

    static List<String> variantIds(Collection<String> gameKeys) {
        var prefix = GameLoader.VARIANTS + ".";
        return gameKeys.stream().filter(key -> key.startsWith(prefix)).map(key -> key.substring(prefix.length()).split("\\.")[0])
                .distinct().sorted().toList();
    }

    static List<String> variantKeys(Collection<String> gameKeys) {
        return gameKeys.stream().filter(key -> !key.equals("gametype") && !key.equals(GameLoader.VARIANTS)
                && !key.startsWith(GameLoader.VARIANTS + ".")).sorted().toList();
    }

    /** A variant of the game with only a name; change the rest with {@link #setVariant}. */
    public void createVariant(CommandSender sender, String game, String variant, String name) {
        if (!IdRegistry.isValid(variant) || RESERVED_IDS.contains(variant)) {
            messages.send(sender, "edit.invalid-id", Placeholder.unparsed("id", variant));
            return;
        }
        var key = GameLoader.VARIANTS + "." + variant;
        edit(sender, Kind.GAME, game, false, config -> {
            if (config.contains(key)) {
                throw new IllegalArgumentException("already has a variant " + variant);
            }
            config.set(key + ".name", name);
        }, "edit.variant-created", Placeholder.unparsed("variant", variant));
    }

    public void setVariant(CommandSender sender, String game, String variant, String key, String text) {
        if (knownVariant(sender, game, variant)) {
            set(sender, Kind.GAME, game, GameLoader.VARIANTS + "." + variant + "." + key, text);
        }
    }

    public void unsetVariant(CommandSender sender, String game, String variant, String key) {
        if (knownVariant(sender, game, variant)) {
            unset(sender, Kind.GAME, game, GameLoader.VARIANTS + "." + variant + "." + key);
        }
    }

    /** Takes the variant out of the game's file; its stats stay in the game's totals. */
    public void deleteVariant(CommandSender sender, String game, String variant) {
        if (!knownVariant(sender, game, variant)) {
            return;
        }
        var key = GameLoader.VARIANTS + "." + variant;
        edit(sender, Kind.GAME, game, false, config -> {
            if (!config.contains(key)) {
                throw new IllegalArgumentException("has no variant " + variant);
            }
            config.set(key, null);
            var variants = config.getConfigurationSection(GameLoader.VARIANTS);
            if (variants != null && variants.getKeys(false).isEmpty()) {
                config.set(GameLoader.VARIANTS, null);
            }
        }, "edit.variant-deleted", Placeholder.unparsed("variant", variant));
    }

    /** Checked against the last load, so the message names the variant rather than a key in it. */
    private boolean knownVariant(CommandSender sender, String game, String variant) {
        if (variantIds(game).contains(variant)) {
            return true;
        }
        messages.send(sender, "edit.unknown-variant", Placeholder.unparsed("file", Kind.GAME.file(game)),
                Placeholder.unparsed("variant", variant));
        return false;
    }

    public Collection<String> gametypeIds() {
        return gametypes.ids();
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

    /** An arena without spawns in the admin's world. */
    public void createArena(Player player, String id) {
        var world = player.getWorld();
        edit(player, Kind.ARENA, id, true, config -> ArenaFile.create(config, world), "arena.created",
                Placeholder.unparsed("arena", id), Placeholder.unparsed("world", world.getName()));
    }

    /**
     * Any other change to an existing file, checked and written like {@link #set}.
     *
     * @param change runs off the server thread; throw {@link IllegalArgumentException} to refuse it with that reason
     * @param done the message once it is saved; gets {@code <file>} on top of the given placeholders
     */
    public void change(CommandSender sender, Kind kind, String id, Consumer<YamlConfiguration> change, String done,
            TagResolver... placeholders) {
        edit(sender, kind, id, false, change, done, placeholders);
    }

    /** A game that waits in the arena, for 2 to 8 players; change the rest with {@link #set}. */
    public void createGame(CommandSender sender, String id, String gametype, String arena) {
        edit(sender, Kind.GAME, id, true, config -> {
            // A game named like a bundled one of the same type starts from it, so it is set up as shipped: some types need teams or ranks to be valid at all.
            var example = bundledGame(id);
            if (example.isPresent() && gametype.equals(example.get().getString("gametype"))) {
                example.get().getKeys(false).forEach(key -> config.set(key, example.get().get(key)));
            } else {
                config.set("gametype", gametype);
                config.set("min-players", 2);
                config.set("max-players", 8);
                config.set("waiting", "arena");
            }
            config.set("arenas", List.of(arena));
        }, "edit.created");
    }

    private Optional<YamlConfiguration> bundledGame(String id) {
        try (var in = plugin.getResource(Kind.GAME.file(id))) {
            return in == null ? Optional.empty() : Optional.of(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** A game night playing one game, for solo players; change the rest with {@link #set} and {@link #change}. */
    public void createNight(CommandSender sender, String id, String game, String name) {
        edit(sender, Kind.NIGHT, id, true, config -> {
            config.set("name", name);
            config.set("teams", "solo");
            config.set("playlist", List.of(game));
        }, "edit.night-created");
    }

    /** A schedule opening one game; change the rest, the games included, with {@link #set}. */
    public void createSchedule(CommandSender sender, String id, String game, String when) {
        edit(sender, Kind.SCHEDULE, id, true, config -> {
            config.set("when", when);
            config.set("games", List.of(game));
        }, "edit.schedule-created");
    }

    /** Arenas, games, game nights and schedules; the file is kept as {@code <file>.deleted}. */
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

    /**
     * Gives an arena another id: its folder (file, saved map and region schematics) and the games that list it.
     * A game file that can't be changed is named, to be fixed by hand.
     */
    public void renameArena(CommandSender sender, String from, String to) {
        if (!IdRegistry.isValid(to) || RESERVED_IDS.contains(to)) {
            messages.send(sender, "edit.invalid-id", Placeholder.unparsed("id", to));
            return;
        }
        if (!ids(Kind.ARENA).contains(from)) {
            messages.send(sender, "edit.unknown", Placeholder.unparsed("file", Kind.ARENA.file(from)));
            return;
        }
        if (from.equals(to) || ids(Kind.ARENA).contains(to)) {
            messages.send(sender, "edit.exists", Placeholder.unparsed("file", Kind.ARENA.file(to)));
            return;
        }
        if (arenaInUse.test(from)) {
            messages.send(sender, "arena.in-use", Placeholder.unparsed("arena", from));
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            var arenas = plugin.getDataPath().resolve("arenas");
            try {
                Files.move(arenas.resolve(from), arenas.resolve(to), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not move arena " + from + " to " + to, e);
                messages.send(sender, "edit.save-failed", Placeholder.unparsed("file", Kind.ARENA.file(to)));
                return;
            }
            var failed = new ArrayList<String>();
            var games = plugin.getDataPath().resolve("games");
            if (Files.isDirectory(games)) {
                try (var files = Files.list(games)) {
                    for (var path : files.filter(name -> name.toString().endsWith(".yml")).sorted().toList()) {
                        try {
                            if (Files.readString(path).contains(from)) {
                                ConfigFiles.update(path, config -> renameArenaIn(config, from, to));
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Could not change " + path + " to use arena " + to, e);
                            failed.add("games/" + path.getFileName());
                        }
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not list " + games, e);
                    failed.add("games");
                }
            }
            plugin.getLogger().info(sender.getName() + " renamed arena " + from + " to " + to);
            messages.send(sender, "arena.renamed", Placeholder.unparsed("arena", from), Placeholder.unparsed("to", to));
            failed.forEach(name -> messages.send(sender, "arena.rename-reference-failed", Placeholder.unparsed("file", name), Placeholder.unparsed("arena", from)));
            reload.accept(sender);
        });
    }

    /** Replaces the id in every {@code arenas} list of the game file, variants included. */
    private static void renameArenaIn(ConfigurationSection section, String from, String to) {
        if (section.isList("arenas")) {
            section.set("arenas", section.getStringList("arenas").stream().map(id -> id.equals(from) ? to : id).toList());
        }
        for (var key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                renameArenaIn(section.getConfigurationSection(key), from, to);
            }
        }
    }

    private boolean editable(CommandSender sender, Kind kind, String id) {
        if (kind == Kind.CONFIG) {
            return true;
        }
        if (!IdRegistry.isValid(id) || RESERVED_IDS.contains(id)) {
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
                    return onServerThread(() -> check(kind, id, config));
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
     */
    private List<ConfigProblem> check(Kind kind, String id, YamlConfiguration config) {
        var worlds = plugin.getServer();
        return switch (kind) {
            case CONFIG -> SettingsLoader.load(config.saveToString(), worlds::getWorld).problems();
            case ARENA -> ArenaLoader.load(new YamlFiles("arenas", ArenaLoader.LAYOUT, Map.of(id, config), List.of()), worlds::getWorld).problems();
            case GAME -> gameLoader.load(new YamlFiles("games", Map.of(id, config), List.of()), arenas.get(), settings.get().lobby().isPresent(), settings.get().hud(), messages::has)
                    .problems();
            case NIGHT -> NightTemplateLoader.load(new YamlFiles(NightTemplateLoader.FOLDER, Map.of(id, config), List.of()), games.get()).problems();
            case SCHEDULE -> ScheduleLoader.load(new YamlFiles(ScheduleLoader.FOLDER, Map.of(id, config), List.of()), games.get()).problems();
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
