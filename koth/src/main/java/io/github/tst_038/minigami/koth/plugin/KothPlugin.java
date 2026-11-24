package io.github.tst_038.minigami.koth.plugin;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.api.reward.RewardReader;
import io.github.tst_038.minigami.api.stat.MiniGamiStats;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import io.github.tst_038.minigami.koth.command.KothCommands;
import io.github.tst_038.minigami.koth.config.KothConfigLoader;
import io.github.tst_038.minigami.koth.config.KothFiles;
import io.github.tst_038.minigami.koth.ledger.Balances;
import io.github.tst_038.minigami.koth.ledger.PointsLedger;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import io.github.tst_038.minigami.koth.round.CombatListener;
import io.github.tst_038.minigami.koth.placeholder.KothExpansion;
import io.github.tst_038.minigami.koth.round.KothRuntime;
import io.github.tst_038.minigami.koth.sidebar.KothSidebar;
import io.github.tst_038.minigami.koth.shop.Checkout;
import io.github.tst_038.minigami.koth.shop.Shop;
import io.github.tst_038.minigami.koth.shop.ShopListener;
import io.github.tst_038.minigami.koth.shop.ShopReader;
import io.github.tst_038.minigami.koth.stats.StatBuffer;
import io.github.tst_038.minigami.koth.status.KothPlaceholders;
import io.github.tst_038.minigami.koth.world.KothWorld;
import io.github.tst_038.minigami.koth.world.RespawnListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.plugin.java.JavaPlugin;

/** King of the Hill as a plugin of its own, on top of MiniGami's stats, saved items and players. */
public final class KothPlugin extends JavaPlugin {

    private final AtomicInteger configLoads = new AtomicInteger();
    /** Who asked for a load that hasn't finished; the newest load answers them all, also those of loads it overtook. */
    private final Queue<Audience> requesters = new ConcurrentLinkedQueue<>();
    private Storage storage;
    private KothRuntime runtime;
    private KothSidebar sidebar;
    private RewardReader rewards;
    private volatile Shop shop = Shop.NONE;

    /** What the shop sells now; {@link Shop#NONE} until a usable {@code shop.yml} has been read. */
    public Shop shop() {
        return shop;
    }

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
        storage = new SqliteStorage(getDataPath().resolve("koth.db"), new Migrations(getClass().getClassLoader(), PointsLedger.MIGRATIONS), "MiniGamiKoth storage", getLogger());
        Executor serverThread = task -> getServer().getGlobalRegionScheduler().execute(this, task);
        var wallet = new Wallet(new PointsLedger(storage), new Balances(), serverThread, getLogger());
        var buffer = new StatBuffer();
        runtime = new KothRuntime(getServer(), messages, wallet, stats, players, buffer, Clock.systemDefaultZone(), RandomGenerator.getDefault());

        getServer().getPluginManager().registerEvents(new BalanceListener(wallet), this);
        getServer().getPluginManager().registerEvents(new CombatListener(runtime, buffer, players), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(runtime::config), this);
        rewards = new RewardReader(savedItems, getLogger());
        getServer().getPluginManager().registerEvents(new ShopListener(new Checkout(getServer(), messages, wallet, players, serverThread, getLogger())), this);
        getServer().getOnlinePlayers().forEach(player -> wallet.load(player.getUniqueId()));
        sidebar = new KothSidebar(getServer(), messages, players);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            KothExpansion.register(this, new KothPlaceholders(runtime::status, wallet.balances(), Clock.systemDefaultZone()));
        }
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            runtime.second();
            sidebar.update(runtime.config(), runtime.status(), LocalDateTime.now());
        }, 20, 20);
        var commands = new KothCommands(getServer(), messages, runtime, wallet, players, this::shop, requester -> loadConfig(files, messages, requester), serverThread, getLogger());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(commands.playerRoot(), "King of the Hill: status, shop, points and going there");
            event.registrar().register(commands.adminRoot(), "Manage King of the Hill");
        });
        loadConfig(files, messages, null);
    }

    /**
     * Reads the files off the server thread, then applies them on it, unless a newer load has started since.
     *
     * @param requester who is told how it went, if anyone; the newest load answers everyone waiting
     */
    private void loadConfig(KothFiles files, Messages messages, Audience requester) {
        if (requester != null) {
            requesters.add(requester);
        }
        var load = configLoads.incrementAndGet();
        getServer().getAsyncScheduler().runNow(this, task -> {
            var problems = new ArrayList<ConfigProblem>();
            var configText = files.readConfig(problems);
            var shopText = files.readShop(problems);
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
                var loadedShop = ShopReader.load(shopText, rewards);
                problems.addAll(loadedShop.problems());
                loadedShop.shop().ifPresent(read -> shop = read);
                problems.forEach(problem -> getLogger().warning(problem.toString()));
                if (loaded.config().isEmpty()) {
                    getLogger().warning("KOTH has no usable config.yml yet, so it stays off or keeps the one it had.");
                }
                if (loadedShop.shop().isEmpty()) {
                    getLogger().warning("KOTH has no usable shop.yml yet, so the shop stays closed or keeps the one it had.");
                }
                tell(drain(), messages, problems, loaded.config().isPresent(), loadedShop.shop().isPresent());
            });
        });
    }

    private List<Audience> drain() {
        var waiting = new ArrayList<Audience>();
        for (Audience audience; (audience = requesters.poll()) != null; ) {
            waiting.add(audience);
        }
        return waiting;
    }

    private void tell(List<Audience> waiting, Messages messages, List<ConfigProblem> problems, boolean configUsed, boolean shopUsed) {
        for (Audience audience : waiting) {
            problems.forEach(problem -> messages.send(audience, "admin.reload-problem", Placeholder.unparsed("problem", problem.toString())));
            if (!configUsed) {
                messages.send(audience, "admin.reload-kept", Placeholder.unparsed("file", KothFiles.CONFIG));
            }
            if (!shopUsed) {
                messages.send(audience, "admin.reload-kept", Placeholder.unparsed("file", KothFiles.SHOP));
            }
            if (configUsed && shopUsed) {
                messages.send(audience, "admin.reload-done");
            }
        }
    }

    @Override
    public void onDisable() {
        // First, while MiniGami is still enabled to take the last stats.
        if (runtime != null) {
            runtime.shutdown();
        }
        if (sidebar != null) {
            sidebar.shutdown();
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
