package io.github.tst_038.minigami.koth.plugin;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.api.stat.MiniGamiStats;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import io.github.tst_038.minigami.koth.config.KothConfigLoader;
import io.github.tst_038.minigami.koth.config.KothFiles;
import io.github.tst_038.minigami.koth.ledger.Balances;
import io.github.tst_038.minigami.koth.ledger.PointsLedger;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import io.github.tst_038.minigami.koth.round.CombatListener;
import io.github.tst_038.minigami.koth.round.KothRuntime;
import io.github.tst_038.minigami.koth.stats.StatBuffer;
import io.github.tst_038.minigami.koth.world.KothWorld;
import io.github.tst_038.minigami.koth.world.RespawnListener;
import java.time.Clock;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/** King of the Hill as a plugin of its own, on top of MiniGami's stats, saved items and players. */
public final class KothPlugin extends JavaPlugin {

    private final AtomicInteger configLoads = new AtomicInteger();
    private Storage storage;
    private KothRuntime runtime;

    @Override
    public void onEnable() {
        MiniGamiStats stats = service(MiniGamiStats.class);
        MiniGamiSavedItems savedItems = service(MiniGamiSavedItems.class);
        MiniGamiPlayers players = service(MiniGamiPlayers.class);
        if (stats == null || savedItems == null || players == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var files = new KothFiles(this);
        var messages = new Messages(files.bundledMessages(), getLogger());
        storage = new SqliteStorage(getDataPath().resolve("koth.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "MiniGamiKoth storage", getLogger());
        Executor serverThread = task -> getServer().getGlobalRegionScheduler().execute(this, task);
        var wallet = new Wallet(new PointsLedger(storage), new Balances(), serverThread, getLogger());
        var buffer = new StatBuffer();
        runtime = new KothRuntime(getServer(), messages, wallet, stats, players, buffer, Clock.systemDefaultZone(), RandomGenerator.getDefault());

        getServer().getPluginManager().registerEvents(new BalanceListener(wallet), this);
        getServer().getPluginManager().registerEvents(new CombatListener(runtime, buffer, players), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(runtime::config), this);
        getServer().getOnlinePlayers().forEach(player -> wallet.load(player.getUniqueId()));
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> runtime.second(), 20, 20);
        loadConfig(files, messages);
    }

    /** Reads the files off the server thread, then applies them on it, unless a newer load has started since. */
    private void loadConfig(KothFiles files, Messages messages) {
        var load = configLoads.incrementAndGet();
        getServer().getAsyncScheduler().runNow(this, task -> {
            var problems = new ArrayList<ConfigProblem>();
            var configText = files.readConfig(problems);
            var messageTexts = files.readMessages(problems);
            if (!isEnabled()) {
                return;
            }
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                if (load != configLoads.get()) {
                    return;
                }
                messages.replace(messageTexts);
                var loaded = KothConfigLoader.load(configText, new KothWorld(getServer()));
                problems.addAll(loaded.problems());
                loaded.config().ifPresent(runtime::configure);
                problems.forEach(problem -> getLogger().warning(problem.toString()));
                if (loaded.config().isEmpty()) {
                    getLogger().warning("KOTH has no usable config.yml yet, so it stays off or keeps the one it had.");
                }
            });
        });
    }

    @Override
    public void onDisable() {
        // First, while MiniGami is still enabled to take the last stats.
        if (runtime != null) {
            runtime.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private <T> T service(Class<T> type) {
        T service = getServer().getServicesManager().load(type);
        if (service == null) {
            getLogger().severe("MiniGami does not offer " + type.getSimpleName() + ", which MiniGamiKoth needs. Switching MiniGamiKoth off.");
        }
        return service;
    }
}
