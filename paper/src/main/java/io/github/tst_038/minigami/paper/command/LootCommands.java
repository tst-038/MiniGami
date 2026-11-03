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
import io.github.tst_038.minigami.paper.loot.LootAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/**
 * {@code /mg loot list|info <table>|roll <table>|save <name>|delete <name>}: look at the loot tables, try one, and keep items
 * for tables to name. A table of a style is written {@code christmas/rare}; the containers themselves are set under {@code /mg arena}.
 */
final class LootCommands {

    private LootCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(LootAdmin loot, Command<CommandSourceStack> help) {
        return Commands.literal("loot")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> loot.list(sender))))
                .then(Commands.literal("info").then(table(loot).executes(bySender((sender, context) -> loot.info(sender, string(context, "table"))))))
                .then(Commands.literal("roll").then(table(loot).executes(bySender((sender, context) -> loot.roll(sender, string(context, "table"))))))
                .then(Commands.literal("save").requires(PLAYER)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(suggest(loot::savedNames))
                                .executes(byPlayer((player, context) -> loot.save(player, string(context, "name"))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(suggest(loot::savedNames))
                                .executes(bySender((sender, context) -> loot.delete(sender, string(context, "name"))))));
    }

    /** Greedy, as a table id has slashes, which a word doesn't take. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> table(LootAdmin loot) {
        return Commands.argument("table", StringArgumentType.greedyString()).suggests(suggest(loot::tableIds));
    }
}
