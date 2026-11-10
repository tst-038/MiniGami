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
import java.util.Optional;
import java.util.function.Supplier;

/** {@code /mga gametype list|<id> info} and {@code /mga options [gametype]}: what games can be made of. */
final class GameTypeCommands {

    private GameTypeCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> game(ConfigInfo info, Supplier<Collection<String>> ids, Command<CommandSourceStack> help) {
        return Commands.literal("gametype")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> info.listGametypes(sender))))
                .then(Commands.argument("game", StringArgumentType.word())
                        .suggests(suggest(ids))
                        .executes(help)
                        .then(Commands.literal("info").executes(bySender((sender, context) -> info.gametype(sender, string(context, "game"))))));
    }

    static ArgumentBuilder<CommandSourceStack, ?> options(ConfigInfo info, Supplier<Collection<String>> gametypes) {
        return Commands.literal("options")
                .requires(ADMIN)
                .executes(bySender((sender, context) -> info.options(sender, Optional.empty())))
                .then(Commands.argument("gametype", StringArgumentType.word())
                        .suggests(suggest(gametypes))
                        .executes(bySender((sender, context) -> info.options(sender, Optional.of(string(context, "gametype"))))));
    }
}
