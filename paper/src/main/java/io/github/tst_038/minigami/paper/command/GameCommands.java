package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.function.Supplier;

/** {@code /mg game list|<id> info} and {@code /mg feature list|<id> info}: what modes can be made of. */
final class GameCommands {

    private GameCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> game(ConfigInfo info, Supplier<Collection<String>> ids, Command<CommandSourceStack> help) {
        return Commands.literal("game")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> info.listGames(sender))))
                .then(Commands.argument("game", StringArgumentType.word())
                        .suggests(suggest(ids))
                        .executes(help)
                        .then(Commands.literal("info").executes(bySender((sender, context) -> info.game(sender, string(context, "game"))))));
    }

    static ArgumentBuilder<CommandSourceStack, ?> feature(ConfigInfo info, Supplier<Collection<String>> ids, Command<CommandSourceStack> help) {
        return Commands.literal("feature")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> info.listFeatures(sender))))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests(suggest(ids))
                        .executes(help)
                        .then(Commands.literal("info").executes(bySender((sender, context) -> info.feature(sender, string(context, "feature"))))));
    }
}
