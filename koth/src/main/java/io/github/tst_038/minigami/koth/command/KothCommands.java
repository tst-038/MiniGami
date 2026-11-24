package io.github.tst_038.minigami.koth.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import io.github.tst_038.minigami.koth.message.StatusResolver;
import io.github.tst_038.minigami.koth.round.KothRuntime;
import io.github.tst_038.minigami.koth.shop.Shop;
import io.github.tst_038.minigami.koth.shop.ShopMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /koth [shop|balance|join]} for players, and {@code /kotha reload|start [minutes]|stop|status|points give|take|show <player> [amount]}
 * for staff. Server thread only.
 */
public final class KothCommands {

    public static final String PLAY = "minigami.koth.play";
    public static final String ADMIN = "minigami.koth.admin";

    private enum Points {
        SHOW("admin.points-shown"), GIVE("admin.points-given"), TAKE("admin.points-taken");

        private final String message;

        Points(String message) {
            this.message = message;
        }
    }

    private final Server server;
    private final Messages messages;
    private final KothRuntime runtime;
    private final Wallet wallet;
    private final MiniGamiPlayers players;
    private final Supplier<Shop> shop;
    private final Consumer<Audience> reload;
    private final Executor serverThread;
    private final Logger logger;

    public KothCommands(Server server, Messages messages, KothRuntime runtime, Wallet wallet, MiniGamiPlayers players, Supplier<Shop> shop,
            Consumer<Audience> reload, Executor serverThread, Logger logger) {
        this.server = server;
        this.messages = messages;
        this.runtime = runtime;
        this.wallet = wallet;
        this.players = players;
        this.shop = shop;
        this.reload = reload;
        this.serverThread = serverThread;
        this.logger = logger;
    }

    public LiteralCommandNode<CommandSourceStack> playerRoot() {
        return Commands.literal("koth")
                .requires(source -> source.getSender().hasPermission(PLAY))
                .executes(bySender(this::info))
                .then(Commands.literal("shop").requires(KothCommands::isPlayer).executes(byPlayer(this::openShop)))
                .then(Commands.literal("balance").requires(KothCommands::isPlayer).executes(byPlayer(this::balance)))
                .then(Commands.literal("join").requires(KothCommands::isPlayer).executes(byPlayer(this::join)))
                .build();
    }

    public LiteralCommandNode<CommandSourceStack> adminRoot() {
        return Commands.literal("kotha")
                .requires(source -> source.getSender().hasPermission(ADMIN))
                .then(Commands.literal("reload").executes(bySender(reload::accept)))
                .then(Commands.literal("start")
                        .executes(bySender(sender -> start(sender, Optional.empty())))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    start(context.getSource().getSender(), Optional.of(Duration.ofMinutes(IntegerArgumentType.getInteger(context, "minutes"))));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("stop").executes(bySender(this::stop)))
                .then(Commands.literal("status").executes(bySender(this::adminStatus)))
                .then(Commands.literal("points")
                        .then(Commands.literal("show").then(target().executes(context -> points(context, Points.SHOW, 0))))
                        .then(Commands.literal("give").then(target().then(amount().executes(context -> points(context, Points.GIVE, amountOf(context))))))
                        .then(Commands.literal("take").then(target().then(amount().executes(context -> points(context, Points.TAKE, amountOf(context)))))))
                .build();
    }

    // Players.

    private void info(CommandSender sender) {
        if (runtime.config().isEmpty()) {
            messages.send(sender, "status.not-configured");
        } else {
            var status = runtime.status();
            messages.send(sender, status.running().isPresent() ? "status.active" : "status.inactive", StatusResolver.of(messages, status, LocalDateTime.now()));
        }
    }

    private void openShop(Player player) {
        var current = shop.get();
        if (current == Shop.NONE) {
            messages.send(player, "shop.unavailable");
        } else if (players.isInMatch(player.getUniqueId())) {
            messages.send(player, "shop.in-match");
        } else {
            player.openInventory(ShopMenu.create(current, player, messages));
        }
    }

    private void balance(Player player) {
        var id = player.getUniqueId();
        var cached = wallet.balances().of(id);
        if (cached.isPresent()) {
            messages.send(player, "balance.own", Placeholder.unparsed("points", String.valueOf(cached.getAsLong())));
        } else {
            // Not read yet, as just after joining; ask again.
            wallet.load(id);
            messages.send(player, "balance.loading");
        }
    }

    private void join(Player player) {
        var config = runtime.config();
        if (config.isEmpty()) {
            messages.send(player, "status.not-configured");
        } else if (players.isInMatch(player.getUniqueId())) {
            messages.send(player, "join.in-match");
        } else {
            player.teleportAsync(config.get().spawnOrDefault()).thenAcceptAsync(moved -> {
                if (moved) {
                    messages.send(player, "join.done");
                }
            }, serverThread);
        }
    }

    // Staff.

    private void start(CommandSender sender, Optional<Duration> length) {
        if (runtime.config().isEmpty()) {
            messages.send(sender, "status.not-configured");
        } else {
            messages.send(sender, runtime.forceStart(length) ? "admin.started" : "admin.already-running");
        }
    }

    private void stop(CommandSender sender) {
        if (runtime.config().isEmpty()) {
            messages.send(sender, "status.not-configured");
        } else {
            messages.send(sender, runtime.forceStop() ? "admin.stopped" : "admin.not-running");
        }
    }

    private void adminStatus(CommandSender sender) {
        var status = runtime.status();
        var config = runtime.config();
        var taker = status.taker().map(server::getPlayer).map(Player::getName);
        messages.send(sender, "admin.status", StatusResolver.of(messages, status, LocalDateTime.now()),
                Placeholder.component("state", messages.render(status.running().isPresent() ? "admin.state-running" : "admin.state-idle")),
                Placeholder.component("taker", taker.isPresent() ? Component.text(taker.get()) : messages.render("placeholder.no-holder")),
                Placeholder.unparsed("progress", String.valueOf(status.progress())),
                Placeholder.unparsed("capture", String.valueOf(config.map(found -> found.point().captureSeconds()).orElse(0))),
                Placeholder.unparsed("online", String.valueOf(server.getOnlinePlayers().size())),
                Placeholder.component("config", messages.render(config.isPresent() ? "admin.loaded" : "admin.not-loaded")),
                Placeholder.component("shop", messages.render(shop.get() != Shop.NONE ? "admin.loaded" : "admin.not-loaded")));
    }

    private int points(CommandContext<CommandSourceStack> context, Points what, long amount) {
        var sender = context.getSource().getSender();
        var name = StringArgumentType.getString(context, "player");
        // From the user cache only, so it never blocks.
        OfflinePlayer target = server.getPlayerExact(name) != null ? server.getPlayerExact(name) : server.getOfflinePlayerIfCached(name);
        if (target == null) {
            messages.send(sender, "admin.unknown-player", Placeholder.unparsed("player", name));
            return Command.SINGLE_SUCCESS;
        }
        var id = target.getUniqueId();
        CompletableFuture<Long> result = switch (what) {
            case SHOW -> wallet.balance(id);
            case GIVE -> wallet.give(id, amount);
            case TAKE -> wallet.take(id, amount);
        };
        result.whenCompleteAsync((points, failure) -> {
            // The cache is for players who are here; this one was only filled for the command.
            if (server.getPlayer(id) == null) {
                wallet.forget(id);
            }
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not " + what.name().toLowerCase(Locale.ROOT) + " the points of " + name, failure);
                messages.send(sender, "admin.storage-failed");
            } else {
                messages.send(sender, what.message, Placeholder.unparsed("player", target.getName() != null ? target.getName() : name),
                        Placeholder.unparsed("amount", String.valueOf(amount)), Placeholder.unparsed("points", String.valueOf(points)));
            }
        }, serverThread);
        return Command.SINGLE_SUCCESS;
    }

    // Nodes.

    private RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("player", StringArgumentType.word()).suggests((context, builder) -> {
            server.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Long> amount() {
        return Commands.argument("amount", LongArgumentType.longArg(1));
    }

    private static long amountOf(CommandContext<CommandSourceStack> context) {
        return LongArgumentType.getLong(context, "amount");
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.getExecutor() instanceof Player;
    }

    private static Command<CommandSourceStack> bySender(Consumer<CommandSender> action) {
        return context -> {
            action.accept(context.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        };
    }

    /** Only for nodes that require a player. */
    private static Command<CommandSourceStack> byPlayer(Consumer<Player> action) {
        return context -> {
            action.accept((Player) context.getSource().getExecutor());
            return Command.SINGLE_SUCCESS;
        };
    }
}
