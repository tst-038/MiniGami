package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaLoader;
import io.github.tst_038.minigami.paper.command.GamesCommand;
import io.github.tst_038.minigami.paper.game.TestArena;
import io.github.tst_038.minigami.paper.game.TestGame;
import io.github.tst_038.minigami.paper.match.ActiveMatches;
import io.github.tst_038.minigami.paper.match.MatchService;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import io.github.tst_038.minigami.paper.session.SnapshotStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiniGamiPlugin extends JavaPlugin {

    private final IdRegistry<GameType> games = new IdRegistry<>(GameType::id);
    private final IdRegistry<FeatureFactory> features = new IdRegistry<>(FeatureFactory::id);
    private IdRegistry<Arena> arenas = new IdRegistry<>(Arena::id);
    private ActiveMatches matches;
    private SnapshotStore snapshots;

    @Override
    public void onEnable() {
        var messages = new Messages(loadMessages(), getLogger());
        snapshots = new SnapshotStore(getDataPath().resolve("snapshots"), getLogger());
        var sessions = new PlayerSessions(this, snapshots, getLogger());
        getServer().getPluginManager().registerEvents(sessions, this);
        matches = new ActiveMatches(getLogger());
        // shortcut: one hardcoded game on a platform above the main world's spawn; arenas and modes come from config later.
        var world = getServer().getWorlds().getFirst();
        var center = world.getSpawnLocation().add(0, 40, 0);
        center.setY(Math.min(center.getY(), world.getMaxHeight() - 3));
        var arena = new TestArena(center);
        games.register(new TestGame(arena));
        features.register(new VoidKillsFactory());
        var service = new MatchService(this, games.find("test").orElseThrow(), arena::build, sessions, messages, matches);
        getServer().getPluginManager().registerEvents(service, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(GamesCommand.create(service), "Join, leave and manage minigames"));
        loadArenas();
        // The global region scheduler is the main thread on Paper and keeps the door open for Folia.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> matches.tick(), 1, 1);
    }

    private void loadArenas() {
        getServer().getAsyncScheduler().runNow(this, task -> {
            var files = YamlFiles.read(getDataPath().resolve("arenas"));
            if (!isEnabled()) {
                return;
            }
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                var loaded = ArenaLoader.load(files, getServer()::getWorld);
                loaded.problems().forEach(problem -> getLogger().warning("Skipped: " + problem));
                arenas = loaded.arenas();
                getLogger().info("Loaded arenas: " + arenas.ids());
            });
        });
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
