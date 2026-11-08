package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.stats.Leaderboards;
import io.github.tst_038.minigami.paper.stats.StatsAdmin;
import io.github.tst_038.minigami.paper.stats.StatsView;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import org.bukkit.entity.Player;

/** {@code /mg stats [player] [game] [stat]} and {@code /mg leaderboard <game> [stat] [variant]}, for everyone. */
final class StatsCommands {

    private static final String CONFIRM = "confirm";

    private StatsCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> stats(StatsView view, Messages messages) {
        return Commands.literal("stats")
                .executes(bySender((sender, context) -> {
                    if (context.getSource().getExecutor() instanceof Player player) {
                        view.show(sender, player, Optional.empty());
                    } else {
                        messages.send(sender, "stats.name-a-player");
                    }
                }))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(suggest(view::onlineNames))
                        .executes(bySender((sender, context) -> view.show(sender, string(context, "player"), Optional.empty())))
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(suggest(view::gameIds))
                                .executes(bySender((sender, context) ->
                                        view.show(sender, string(context, "player"), Optional.of(string(context, "game")))))
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests(suggest(context -> view.boardStats(string(context, "game"))))
                                        .executes(bySender((sender, context) -> view.show(sender, string(context, "player"),
                                                Optional.of(string(context, "game")), Optional.of(string(context, "stat"))))))));
    }

    static ArgumentBuilder<CommandSourceStack, ?> leaderboard(StatsView view) {
        return Commands.literal("leaderboard")
                .then(Commands.argument("game", StringArgumentType.word())
                        .suggests(suggest(view::gameIds))
                        .executes(bySender((sender, context) -> view.top(sender, string(context, "game"), Leaderboards.WINS, Optional.empty())))
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests(suggest(context -> view.boardStats(string(context, "game"))))
                                .executes(bySender((sender, context) ->
                                        view.top(sender, string(context, "game"), string(context, "stat"), Optional.empty())))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests(suggest(context -> view.variantIds(string(context, "game"))))
                                        .executes(bySender((sender, context) -> view.top(sender, string(context, "game"),
                                                string(context, "stat"), Optional.of(string(context, "variant"))))))));
    }

    /** {@code /mg stats add|set <player> <game> <stat> <amount>}, and {@code reset <player> [game]} and {@code reset-game <game> [variant]}, which ask for {@code confirm}. */
    static ArgumentBuilder<CommandSourceStack, ?> admin(StatsAdmin admin, StatsView view, Messages messages) {
        return Commands.literal("stats")
                .then(Commands.literal("add").then(edit(view, LongArgumentType.longArg(),
                        (sender, context) -> admin.add(sender, string(context, "player"), string(context, "game"), string(context, "stat"),
                                LongArgumentType.getLong(context, "amount")))))
                .then(Commands.literal("set").then(edit(view, LongArgumentType.longArg(0),
                        (sender, context) -> admin.set(sender, string(context, "player"), string(context, "game"), string(context, "stat"),
                                LongArgumentType.getLong(context, "amount")))))
                .then(Commands.literal("reset")
                        .then(player(view)
                                .executes(bySender((sender, context) -> messages.send(sender, "stats.admin.confirm")))
                                .then(Commands.literal(CONFIRM).executes(bySender((sender, context) ->
                                        admin.resetPlayer(sender, string(context, "player"), Optional.empty()))))
                                .then(game(view)
                                        .executes(bySender((sender, context) -> messages.send(sender, "stats.admin.confirm")))
                                        .then(Commands.literal(CONFIRM).executes(bySender((sender, context) ->
                                                admin.resetPlayer(sender, string(context, "player"), Optional.of(string(context, "game")))))))))
                .then(Commands.literal("reset-game")
                        .then(game(view)
                                .executes(bySender((sender, context) -> messages.send(sender, "stats.admin.confirm")))
                                .then(Commands.literal(CONFIRM).executes(bySender((sender, context) ->
                                        admin.resetGame(sender, string(context, "game"), Optional.empty()))))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests(suggest(context -> view.variantIds(string(context, "game"))))
                                        .executes(bySender((sender, context) -> messages.send(sender, "stats.admin.confirm")))
                                        .then(Commands.literal(CONFIRM).executes(bySender((sender, context) ->
                                                admin.resetGame(sender, string(context, "game"), Optional.of(string(context, "variant")))))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> player(StatsView view) {
        return Commands.argument("player", StringArgumentType.word()).suggests(suggest(view::onlineNames));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> game(StatsView view) {
        return Commands.argument("game", StringArgumentType.word()).suggests(suggest(view::gameIds));
    }

    /** {@code <player> <game> <stat> <amount>}, run by the action. */
    private static ArgumentBuilder<CommandSourceStack, ?> edit(StatsView view, com.mojang.brigadier.arguments.ArgumentType<Long> amount,
            Nodes.Action<org.bukkit.command.CommandSender> action) {
        return player(view).then(game(view).then(Commands.argument("stat", StringArgumentType.word())
                .suggests(suggest(context -> view.boardStats(string(context, "game"))))
                .then(Commands.argument("amount", amount).executes(bySender(action)))));
    }
}
