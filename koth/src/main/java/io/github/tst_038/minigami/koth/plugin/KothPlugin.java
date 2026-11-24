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
import io.github.tst_038.minigami.koth.schedule.HandledWindows;
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
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
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
    /** Takes our PlaceholderAPI expansion away; does nothing when PlaceholderAPI isn't there. */
    private Runnable unregisterPlaceholders = () -> { };
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
        var clock = Clock.systemDefaultZone();
        var buffer = new StatBuffer();
        var handledWindows = new HandledWindows(storage);
        runtime = new KothRuntime(getServer(), messages, wallet, stats, players, buffer, clock, RandomGenerator.getDefault(),
                lastDismissed(handledWindows), start -> handledWindows.remember(start).exceptionally(failure -> {
                    getLogger().log(Level.WARNING, "Could not remember that the window of " + start + " was skipped or stopped", failure);
                    return null;
                }));

        getServer().getPluginManager().registerEvents(new BalanceListener(wallet), this);
        getServer().getPluginManager().registerEvents(new CombatListener(runtime, buffer, players), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(runtime::config), this);
        rewards = new RewardReader(savedItems, getLogger());
        getServer().getPluginManager().registerEvents(new ShopListener(new Checkout(getServer(), messages, wallet, players, serverThread, getLogger())), this);
        getServer().getOnlinePlayers().forEach(player -> wallet.load(player.getUniqueId()));
        sidebar = new KothSidebar(getServer(), messages, players, clock);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            unregisterPlaceholders = KothExpansion.register(this, new KothPlaceholders(runtime::status, wallet.balances(), clock));
        }
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            runtime.second();
            sidebar.update(runtime.config(), runtime.status());
        }, 20, 20);
        var commands = new KothCommands(getServer(), messages, runtime, wallet, players, this::shop, requester -> loadConfig(files, messages, requester), clock, serverThread, getLogger());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(commands.playerRoot(), "King of the Hill: status, shop, points and going there");
            event.registrar().register(commands.adminRoot(), "Manage King of the Hill");
        });
        loadConfig(files, messages, null);
    }

    /** shortcut: waits for one small read while enabling; make it async if the database ever gets slow. */
    private Optional<LocalDateTime> lastDismissed(HandledWindows handledWindows) {
        try {
            return handledWindows.last().join();
        } catch (CompletionException e) {
            getLogger().log(Level.WARNING, "Could not read which window was last skipped or stopped, so it may start after all", e.getCause());
            return Optional.empty();
        }
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
                var hadConfig = runtime.config().isPresent();
                var hadShop = shop != Shop.NONE;
                var loaded = KothConfigLoader.load(configText, new KothWorld(getServer()));
                problems.addAll(loaded.problems());
                loaded.config().ifPresent(runtime::configure);
                var loadedShop = ShopReader.load(shopText, rewards);
                problems.addAll(loadedShop.problems());
                loadedShop.shop().ifPresent(read -> shop = read);
                problems.forEach(problem -> getLogger().warning(problem.toString()));
                if (loaded.config().isEmpty()) {
                    getLogger().warning(hadConfig ? "KOTH keeps the config.yml it had, as the new one has problems."
                            : "KOTH has no usable config.yml yet, so it stays off.");
                }
                if (loadedShop.shop().isEmpty()) {
                    getLogger().warning(hadShop ? "KOTH keeps the shop.yml it had, as the new one has problems."
                            : "KOTH has no usable shop.yml yet, so the shop stays closed.");
                }
                tell(drain(), messages, problems, unused(loaded.config().isPresent(), hadConfig), unused(loadedShop.shop().isPresent(), hadShop));
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

    /** @return the message that says a file wasn't used, or null if it was */
    private static String unused(boolean used, boolean hadOne) {
        return used ? null : hadOne ? "admin.reload-kept" : "admin.reload-unusable";
    }

    /** @param configNote what to tell about config.yml, or null if it was used; likewise {@code shopNote} */
    private void tell(List<Audience> waiting, Messages messages, List<ConfigProblem> problems, String configNote, String shopNote) {
        for (Audience audience : waiting) {
            problems.forEach(problem -> messages.send(audience, "admin.reload-problem", Placeholder.unparsed("problem", problem.toString())));
            if (configNote != null) {
                messages.send(audience, configNote, Placeholder.unparsed("file", KothFiles.CONFIG));
            }
            if (shopNote != null) {
                messages.send(audience, shopNote, Placeholder.unparsed("file", KothFiles.SHOP));
            }
            if (configNote == null && shopNote == null) {
                messages.send(audience, "admin.reload-done");
            }
        }
    }

    @Override
    public void onDisable() {
        unregisterPlaceholders.run();
        // The runtime first, while MiniGami is still enabled to take the last stats; the rest must happen even if it throws.
        try {
            if (runtime != null) {
                runtime.shutdown();
            }
        } finally {
            try {
                if (sidebar != null) {
                    sidebar.shutdown();
                }
            } finally {
                if (storage != null) {
                    storage.close();
                }
            }
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
