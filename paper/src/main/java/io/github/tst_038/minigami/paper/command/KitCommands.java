package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.paper.kit.KitAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /mga kit save <name>|list|delete <name>}: inventories saved to give players in a game. */
final class KitCommands {

    private KitCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(KitAdmin kits, Command<CommandSourceStack> help) {
        return Commands.literal("kit")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> kits.list(sender))))
                .then(Commands.literal("save")
                        .requires(PLAYER)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(suggest(kits::names))
                                .executes(byPlayer((player, context) -> kits.save(player, string(context, "name"))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(suggest(kits::names))
                                .executes(bySender((sender, context) -> kits.delete(sender, string(context, "name"))))));
    }
}
