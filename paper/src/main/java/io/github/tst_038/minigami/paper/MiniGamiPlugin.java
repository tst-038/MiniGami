package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.buildbattle.BuildBattle;
import io.github.tst_038.minigami.features.anticamping.AntiCampingFactory;
import io.github.tst_038.minigami.features.breakblocks.BreakBlocksFactory;
import io.github.tst_038.minigami.features.decayfloor.DecayFloorFactory;
import io.github.tst_038.minigami.features.givedrops.GiveDropsFactory;
import io.github.tst_038.minigami.features.kit.KitFactory;
import io.github.tst_038.minigami.features.nodamage.NoDamageFactory;
import io.github.tst_038.minigami.features.projectilebreaks.ProjectileBreaksFactory;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.laststanding.LastStanding;
import io.github.tst_038.minigami.teamdeathmatch.TeamDeathmatch;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaLoader;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.command.GamesCommand;
import io.github.tst_038.minigami.paper.config.Settings;
import io.github.tst_038.minigami.paper.config.SettingsLoader;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.match.ActiveMatches;
import io.github.tst_038.minigami.paper.match.MatchAdmin;
import io.github.tst_038.minigami.paper.match.MatchService;
import io.github.tst_038.minigami.paper.mode.Mode;
import io.github.tst_038.minigami.paper.mode.ModeLoader;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.github.tst_038.minigami.paper.session.SnapshotStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiniGamiPlugin extends JavaPlugin {

    private static final String MESSAGES = "messages/en.yml";

    private final IdRegistry<GameType> games = new IdRegistry<>(GameType::id);
    private final IdRegistry<FeatureFactory> features = new IdRegistry<>(FeatureFactory::id);
    private IdRegistry<Arena> arenas = new IdRegistry<>(Arena::id);
    private IdRegistry<Mode> modes = new IdRegistry<>(Mode::id);
    private Settings settings = Settings.DEFAULTS;
    private ActiveMatches matches;
    private SnapshotStore snapshots;
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
        var sessions = new PlayerSessions(this, snapshots, getLogger());
        getServer().getPluginManager().registerEvents(sessions, this);
        matches = new ActiveMatches(getLogger());
        games.register(new LastStanding());
        games.register(new BuildBattle());
        games.register(new TeamDeathmatch());
        features.register(new VoidKillsFactory());
        features.register(new KitFactory());
        features.register(new BreakBlocksFactory());
        features.register(new NoDamageFactory());
        features.register(new GiveDropsFactory());
        features.register(new ProjectileBreaksFactory());
        features.register(new AntiCampingFactory());
        features.register(new DecayFloorFactory());
        var service = new MatchService(this, () -> modes, () -> settings.lobby(), sessions, messages, matches);
        getServer().getPluginManager().registerEvents(service, this);
        var snapshotAdmin = new SnapshotAdmin(this, snapshots, sessions, messages);
        editor = new ConfigEditor(this, messages, games, features, () -> arenas, () -> settings, service::isArenaInUse, this::loadConfig);
        var arenaSetup = new ArenaSetup(() -> arenas, editor, messages);
        info = new ConfigInfo(messages, editor, games, features, () -> arenas, () -> modes, service::isArenaInUse);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(GamesCommand.create(messages, service, new MatchAdmin(service, messages, getServer()), editor, arenaSetup, info, games.ids(), features.ids(), snapshotAdmin,
                        () -> settings.lobby(), this::loadConfig), "Join, leave and manage minigames"));
        loadConfig(null);
        // The global region scheduler is the main thread on Paper and keeps the door open for Folia.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> matches.tick(), 1, 1);
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
            saveExample("modes");
            var arenaFiles = YamlFiles.read(getDataPath().resolve("arenas"));
            var modeFiles = YamlFiles.read(getDataPath().resolve("modes"));
            if (!isEnabled()) {
                return;
            }
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                if (load != configLoads.get()) {
                    return;
                }
                var loadedSettings = SettingsLoader.load(settingsText, getServer()::getWorld);
                var loadedArenas = ArenaLoader.load(arenaFiles, getServer()::getWorld);
                var loadedModes = new ModeLoader(games, features)
                        .load(modeFiles, loadedArenas.entries(), loadedSettings.settings().lobby().isPresent());
                // Also for files with problems, so they can be fixed by command.
                var problems = new ArrayList<>(fileProblems);
                problems.addAll(loadedSettings.problems());
                problems.addAll(loadedArenas.problems());
                problems.addAll(loadedModes.problems());
                editor.keysLoaded(loadedSettings.keys(), loadedArenas.keys(), loadedModes.keys());
                info.loaded(settingsText, arenaFiles.files(), modeFiles.files(), problems);
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
                modes = loadedModes.entries();
                fileProblems.forEach(problem -> getLogger().warning("Using defaults: " + problem));
                loadedSettings.problems().forEach(problem -> getLogger().warning("Using defaults: " + problem));
                loadedArenas.problems().forEach(problem -> getLogger().warning("Skipped: " + problem));
                loadedModes.problems().forEach(problem -> getLogger().warning("Skipped: " + problem));
                getLogger().info("Loaded arenas: " + arenas.ids() + ", modes: " + modes.ids());
                for (Audience audience : waiting) {
                    messages.send(audience, "reload.done", Formatter.number("arenas", arenas.ids().size()),
                            Formatter.number("modes", modes.ids().size()));
                }
            });
        });
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
    }

    private YamlConfiguration loadBundledMessages() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getResource(MESSAGES)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
