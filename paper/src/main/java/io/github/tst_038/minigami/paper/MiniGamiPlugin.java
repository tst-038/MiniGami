package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.laststanding.LastStanding;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaLoader;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.command.GamesCommand;
import io.github.tst_038.minigami.paper.config.LobbySetup;
import io.github.tst_038.minigami.paper.config.Settings;
import io.github.tst_038.minigami.paper.config.SettingsLoader;
import io.github.tst_038.minigami.paper.match.ActiveMatches;
import io.github.tst_038.minigami.paper.match.MatchService;
import io.github.tst_038.minigami.paper.mode.Mode;
import io.github.tst_038.minigami.paper.mode.ModeLoader;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import io.github.tst_038.minigami.paper.session.SnapshotStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiniGamiPlugin extends JavaPlugin {

    private final IdRegistry<GameType> games = new IdRegistry<>(GameType::id);
    private final IdRegistry<FeatureFactory> features = new IdRegistry<>(FeatureFactory::id);
    private IdRegistry<Arena> arenas = new IdRegistry<>(Arena::id);
    private IdRegistry<Mode> modes = new IdRegistry<>(Mode::id);
    private Settings settings = Settings.DEFAULTS;
    private ActiveMatches matches;
    private SnapshotStore snapshots;
    /** Counts config loads, so one that finishes after a newer one doesn't overwrite it. */
    private final AtomicInteger configLoads = new AtomicInteger();

    @Override
    public void onEnable() {
        var messages = new Messages(loadMessages(), getLogger());
        snapshots = new SnapshotStore(getDataPath().resolve("snapshots"), getLogger());
        var sessions = new PlayerSessions(this, snapshots, getLogger());
        getServer().getPluginManager().registerEvents(sessions, this);
        matches = new ActiveMatches(getLogger());
        games.register(new LastStanding());
        features.register(new VoidKillsFactory());
        var service = new MatchService(this, () -> modes, () -> settings.lobby(), sessions, messages, matches);
        getServer().getPluginManager().registerEvents(service, this);
        var lobbySetup = new LobbySetup(this, messages, this::loadConfig);
        var arenaSetup = new ArenaSetup(this, () -> arenas, service::isArenaInUse, messages, this::loadConfig);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(GamesCommand.create(service, arenaSetup, lobbySetup), "Join, leave and manage minigames"));
        loadConfig();
        // The global region scheduler is the main thread on Paper and keeps the door open for Folia.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> matches.tick(), 1, 1);
    }

    /** Reads the files off the server thread, then validates them on it, since that looks up worlds. */
    private void loadConfig() {
        var load = configLoads.incrementAndGet();
        getServer().getAsyncScheduler().runNow(this, task -> {
            var settingsText = readSettings();
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
                settings = loadedSettings.settings();
                arenas = loadedArenas.entries();
                modes = loadedModes.entries();
                loadedSettings.problems().forEach(problem -> getLogger().warning("Using defaults: " + problem));
                loadedArenas.problems().forEach(problem -> getLogger().warning("Skipped: " + problem));
                loadedModes.problems().forEach(problem -> getLogger().warning("Skipped: " + problem));
                getLogger().info("Loaded arenas: " + arenas.ids() + ", modes: " + modes.ids());
            });
        });
    }

    /** Blocking IO. On a fresh install, writes the bundled example into the folder so admins have one to copy. */
    private void saveExample(String folder) {
        if (!Files.exists(getDataPath().resolve(folder))) {
            saveResource(folder + "/example.yml", false);
        }
    }

    /** Blocking IO. Writes the bundled file first if the admin has none; an unreadable file counts as empty. */
    private String readSettings() {
        var path = getDataPath().resolve(SettingsLoader.FILE);
        try {
            if (!Files.exists(path)) {
                saveResource(SettingsLoader.FILE, false);
            }
            return Files.readString(path);
        } catch (IOException | IllegalArgumentException e) {
            getLogger().log(Level.WARNING, "Can't read " + SettingsLoader.FILE + "; using defaults", e);
            return "";
        }
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

    /** The admin's copy in the data folder, falling back to the bundled text for keys it lacks. */
    private YamlConfiguration loadMessages() {
        var path = "messages/en.yml";
        if (!Files.exists(getDataPath().resolve(path))) {
            saveResource(path, false);
        }
        var texts = YamlConfiguration.loadConfiguration(getDataPath().resolve(path).toFile());
        try (var reader = new InputStreamReader(Objects.requireNonNull(getResource(path)), StandardCharsets.UTF_8)) {
            texts.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return texts;
    }
}
