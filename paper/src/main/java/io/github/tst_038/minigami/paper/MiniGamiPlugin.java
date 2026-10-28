package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.buildbattle.BuildBattle;
import io.github.tst_038.minigami.features.anticamping.AntiCampingFactory;
import io.github.tst_038.minigami.features.breakblocks.BreakBlocksFactory;
import io.github.tst_038.minigami.stats.combat.CombatStats;
import io.github.tst_038.minigami.features.countdown.CountdownFactory;
import io.github.tst_038.minigami.features.deathitems.DeathItemsFactory;
import io.github.tst_038.minigami.features.decayfloor.DecayFloorFactory;
import io.github.tst_038.minigami.features.effects.EffectsFactory;
import io.github.tst_038.minigami.features.freeze.FreezeFactory;
import io.github.tst_038.minigami.features.givedrops.GiveDropsFactory;
import io.github.tst_038.minigami.features.kit.KitFactory;
import io.github.tst_038.minigami.features.nodamage.NoDamageFactory;
import io.github.tst_038.minigami.features.nohunger.NoHungerFactory;
import io.github.tst_038.minigami.features.projectilebreaks.ProjectileBreaksFactory;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.hideandseek.HideAndSeek;
import io.github.tst_038.minigami.kingofthehill.KingOfTheHill;
import io.github.tst_038.minigami.laststanding.LastStanding;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaLoader;
import io.github.tst_038.minigami.paper.arena.ArenaMaps;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.command.GamesCommand;
import io.github.tst_038.minigami.paper.config.Settings;
import io.github.tst_038.minigami.paper.config.SettingsLoader;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.match.ActiveMatches;
import io.github.tst_038.minigami.paper.match.MatchAdmin;
import io.github.tst_038.minigami.paper.match.PublicMatches;
import io.github.tst_038.minigami.paper.match.RunningMatches;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.GameLoader;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.github.tst_038.minigami.paper.session.SnapshotStore;
import io.github.tst_038.minigami.paper.stats.Leaderboards;
import io.github.tst_038.minigami.paper.stats.PlayerProfiles;
import io.github.tst_038.minigami.paper.stats.StatsRecorder;
import io.github.tst_038.minigami.paper.stats.StatsView;
import io.github.tst_038.minigami.paper.storage.Migrations;
import io.github.tst_038.minigami.paper.storage.SqliteStorage;
import io.github.tst_038.minigami.paper.storage.Storage;
import io.github.tst_038.minigami.teamdeathmatch.TeamDeathmatch;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiniGamiPlugin extends JavaPlugin {

    private static final String MESSAGES = "messages/en.yml";
    private static final int LEADERBOARD_SIZE = 10;
    /** Five minutes: boards lag a little behind, but are never read from storage per view. */
    private static final long LEADERBOARD_REFRESH_TICKS = 5 * 60 * 20;

    private final IdRegistry<GameType> gametypes = new IdRegistry<>(GameType::id);
    private final IdRegistry<FeatureFactory> features = new IdRegistry<>(FeatureFactory::id);
    private final IdRegistry<StatGroup> statGroups = new IdRegistry<>(StatGroup::id);
    private final GameLoader gameLoader = new GameLoader(gametypes, features, statGroups);
    private IdRegistry<Arena> arenas = new IdRegistry<>(Arena::id);
    private ArenaMaps maps;
    private IdRegistry<Game> games = new IdRegistry<>(Game::id);
    private Settings settings = Settings.DEFAULTS;
    private ActiveMatches matches;
    private SnapshotStore snapshots;
    private Storage storage;
    private ConfigEditor editor;
    private ConfigInfo info;
    private Messages messages;
    /** Counts config loads, so one that finishes after a newer one doesn't overwrite it. */
    private final AtomicInteger configLoads = new AtomicInteger();
    /** Who asked for a load that hasn't finished; the newest load answers them all, also those of loads it overtook. */
    private final Queue<Audience> requesters = new ConcurrentLinkedQueue<>();
    private YamlConfiguration bundledMessages;

    @Override
    public void onEnable() {
        bundledMessages = loadBundledMessages();
        // Until the first load has read the admin's copy.
        messages = new Messages(bundledMessages, getLogger());
        snapshots = new SnapshotStore(getDataPath().resolve("snapshots"), getLogger());
        storage = new SqliteStorage(getDataPath().resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), getLogger());
        Executor serverThread = task -> getServer().getGlobalRegionScheduler().execute(this, task);
        var profiles = new PlayerProfiles(storage, serverThread, Clock.systemUTC(), getLogger());
        getServer().getPluginManager().registerEvents(profiles, this);
        // Players already online after a reload of the plugin.
        getServer().getOnlinePlayers().forEach(player -> profiles.join(player.getUniqueId(), player.getName()));
        var leaderboards = new Leaderboards(storage, serverThread, getLogger(), this::cachedName, LEADERBOARD_SIZE);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> leaderboards.refresh(), 1, LEADERBOARD_REFRESH_TICKS);
        getServer().getPluginManager().registerEvents(new StatsRecorder(storage, profiles, getLogger()), this);
        var sessions = new PlayerSessions(this, snapshots, getLogger());
        getServer().getPluginManager().registerEvents(sessions, this);
        matches = new ActiveMatches(getLogger());
        gametypes.register(new LastStanding());
        gametypes.register(new BuildBattle());
        gametypes.register(new TeamDeathmatch());
        gametypes.register(new HideAndSeek());
        gametypes.register(new KingOfTheHill());
        features.register(new VoidKillsFactory());
        features.register(new KitFactory());
        features.register(new DeathItemsFactory());
        features.register(new BreakBlocksFactory());
        features.register(new NoDamageFactory());
        features.register(new GiveDropsFactory());
        features.register(new ProjectileBreaksFactory());
        features.register(new AntiCampingFactory());
        features.register(new DecayFloorFactory());
        features.register(new NoHungerFactory());
        features.register(new CountdownFactory());
        features.register(new FreezeFactory());
        features.register(new EffectsFactory());
        statGroups.register(new CombatStats());
        maps = new ArenaMaps(this, messages);
        var running = new RunningMatches(this, () -> settings.lobby(), sessions, messages, matches, maps);
        getServer().getPluginManager().registerEvents(running, this);
        var publicMatches = new PublicMatches(this, () -> games, messages, running);
        var snapshotAdmin = new SnapshotAdmin(this, snapshots, sessions, messages);
        editor = new ConfigEditor(this, messages, gametypes, gameLoader, () -> arenas, () -> settings, running::isArenaInUse, this::loadConfig);
        var arenaSetup = new ArenaSetup(() -> arenas, editor, maps, running::isArenaInUse, messages, this);
        info = new ConfigInfo(messages, editor, gametypes, features, () -> arenas, () -> games, running::isArenaInUse);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(GamesCommand.create(messages, publicMatches, running, new MatchAdmin(running, messages, getServer()), editor, arenaSetup, info, gametypes.ids(), features.ids(), snapshotAdmin,
                        new StatsView(profiles, leaderboards, () -> games, getServer(), messages, getLogger()), () -> settings.lobby(), this::loadConfig), "Join, leave and manage minigames"));
        loadConfig(null);
        // The global region scheduler is the main thread on Paper and keeps the door open for Folia.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> matches.tick(), 1, 1);
    }

    /** From the server's user cache only, so it never blocks; empty for players it has forgotten. */
    private Optional<String> cachedName(UUID id) {
        var profile = getServer().createProfile(id);
        return profile.completeFromCache() ? Optional.ofNullable(profile.getName()) : Optional.empty();
    }

    /**
     * Reads the files, messages included, off the server thread, then validates them on it, since that looks up worlds.
     * At startup whatever is valid is loaded, so the server comes up; after that, a load with any
     * problem changes nothing, and running matches keep the config they started with either way.
     *
     * @param requester who asked for the load and hears how it went; {@code null} at startup
     */
    private void loadConfig(Audience requester) {
        if (requester != null) {
            requesters.add(requester);
        }
        var load = configLoads.incrementAndGet();
        getServer().getAsyncScheduler().runNow(this, task -> {
            var fileProblems = new ArrayList<ConfigProblem>();
            var settingsText = readSettings(fileProblems);
            var messageTexts = readMessages(fileProblems);
            saveExample("arenas");
            saveExample("games");
            warnOldModes();
            var arenaFiles = YamlFiles.read(getDataPath().resolve("arenas"));
            var gameFiles = YamlFiles.read(getDataPath().resolve("games"));
            if (!isEnabled()) {
                return;
            }
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                if (load != configLoads.get()) {
                    return;
                }
                var loadedSettings = SettingsLoader.load(settingsText, getServer()::getWorld);
                var loadedArenas = ArenaLoader.load(arenaFiles, getServer()::getWorld);
                var loadedGames = gameLoader.load(gameFiles, loadedArenas.entries(), loadedSettings.settings().lobby().isPresent(), loadedSettings.settings().hud());
                // Also for files with problems, so they can be fixed by command.
                var problems = new ArrayList<>(fileProblems);
                problems.addAll(loadedSettings.problems());
                problems.addAll(loadedArenas.problems());
                problems.addAll(loadedGames.problems());
                editor.keysLoaded(loadedSettings.keys(), loadedArenas.keys(), loadedGames.keys());
                info.loaded(settingsText, arenaFiles.files(), gameFiles.files(), problems);
                var waiting = new ArrayList<Audience>();
                for (Audience next; (next = requesters.poll()) != null; ) {
                    waiting.add(next);
                }
                if (!waiting.isEmpty() && !problems.isEmpty()) {
                    problems.forEach(problem -> getLogger().warning("Not reloaded: " + problem));
                    for (Audience audience : waiting) {
                        messages.send(audience, "reload.failed", Formatter.number("problems", problems.size()));
                        problems.forEach(problem -> messages.send(audience, "reload.problem", Placeholder.unparsed("problem", problem.toString())));
                    }
                    return;
                }
                messages.replace(messageTexts);
                settings = loadedSettings.settings();
                arenas = loadedArenas.entries();
                games = loadedGames.entries();
                fileProblems.forEach(problem -> getLogger().warning("Using defaults: " + problem));
                loadedSettings.problems().forEach(problem -> getLogger().warning("Using defaults: " + problem));
                loadedArenas.problems().forEach(problem -> getLogger().warning("Skipped: " + problem));
                loadedGames.problems().forEach(problem -> getLogger().warning("Skipped: " + problem));
                getLogger().info("Loaded arenas: " + arenas.ids() + ", games: " + games.ids());
                if (requester == null) {
                    // A match the server stopped in the middle of left its arena changed.
                    maps.resetAll(arenas.ids().stream().map(id -> arenas.find(id).orElseThrow()).toList());
                }
                for (Audience audience : waiting) {
                    messages.send(audience, "reload.done", Formatter.number("arenas", arenas.ids().size()),
                            Formatter.number("games", games.ids().size()));
                }
            });
        });
    }

    /** Blocking IO. Games used to be called modes; their old files load no more until moved to games/ and converted. */
    private void warnOldModes() {
        var folder = getDataPath().resolve("modes");
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var paths = Files.list(folder)) {
            var old = paths.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".yml")).sorted().toList();
            if (!old.isEmpty()) {
                getLogger().warning("The modes/ folder is no longer read; convert " + old + " into games/<id>.yml, with variants under 'variants:'");
            }
        } catch (IOException e) {
            getLogger().warning("Can't read the old modes/ folder: " + e.getMessage());
        }
    }

    /** Blocking IO. On a fresh install, writes the bundled example into the folder so admins have one to copy. */
    private void saveExample(String folder) {
        if (!Files.exists(getDataPath().resolve(folder))) {
            saveResource(folder + "/example.yml", false);
        }
    }

    /** Blocking IO. Writes the bundled file first if the admin has none; an unreadable file counts as empty and is a problem. */
    private String readSettings(List<ConfigProblem> problems) {
        try {
            return readOrSaveBundled(SettingsLoader.FILE);
        } catch (IOException | IllegalArgumentException e) {
            problems.add(new ConfigProblem(SettingsLoader.FILE, "", "can't be read: " + e.getMessage()));
            return "";
        }
    }

    /**
     * Blocking IO. The admin's copy, falling back to the bundled text for keys it lacks; just the
     * bundled text, and a problem, if the copy can't be read.
     */
    private YamlConfiguration readMessages(List<ConfigProblem> problems) {
        var texts = new YamlConfiguration();
        try {
            texts.loadFromString(readOrSaveBundled(MESSAGES));
        } catch (IOException | IllegalArgumentException | InvalidConfigurationException e) {
            problems.add(new ConfigProblem(MESSAGES, "", "can't be read: " + e.getMessage()));
            texts = new YamlConfiguration();
        }
        texts.setDefaults(bundledMessages);
        return texts;
    }

    private String readOrSaveBundled(String file) throws IOException {
        var path = getDataPath().resolve(file);
        if (!Files.exists(path)) {
            saveResource(file, false);
        }
        return Files.readString(path);
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.disposeAll();
        }
        // After the matches: disposing them restores players and queues the deletes of their snapshot files.
        if (snapshots != null) {
            snapshots.close();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private YamlConfiguration loadBundledMessages() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getResource(MESSAGES)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
