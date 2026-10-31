package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.stats.Leaderboards;
import io.github.tst_038.minigami.paper.stats.StatsView;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import org.bukkit.entity.Player;

/** {@code /mg stats [player] [game] [stat]} and {@code /mg leaderboard <game> [stat] [variant]}, for everyone. */
final class StatsCommands {

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
}
