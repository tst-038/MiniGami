package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.HOST;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.player;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.paper.night.Nights;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

/**
 * {@code /mg night join|leave|standings} for players, and for hosts
 * {@code start <night>|begin|next|skip|pause|resume|end|kick <player>|team <player> <team>}.
 */
final class NightCommands {

    private NightCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(Nights nights, Command<CommandSourceStack> help) {
        return Commands.literal("night")
                .executes(help)
                .then(Commands.literal("join").requires(PLAYER).executes(byPlayer((player, context) -> nights.join(player))))
                .then(Commands.literal("leave").requires(PLAYER).executes(byPlayer((player, context) -> nights.leave(player))))
                .then(Commands.literal("standings").executes(bySender((sender, context) -> nights.standings(sender))))
                .then(Commands.literal("start").requires(HOST)
                        .then(Commands.argument("night", StringArgumentType.word())
                                .suggests(suggest(nights::templateIds))
                                .executes(bySender((sender, context) -> nights.start(sender, string(context, "night"))))))
                .then(Commands.literal("begin").requires(HOST).executes(bySender((sender, context) -> nights.begin(sender))))
                .then(Commands.literal("next").requires(HOST).executes(bySender((sender, context) -> nights.next(sender))))
                .then(Commands.literal("skip").requires(HOST).executes(bySender((sender, context) -> nights.skip(sender))))
                .then(Commands.literal("pause").requires(HOST).executes(bySender((sender, context) -> nights.pause(sender))))
                .then(Commands.literal("resume").requires(HOST).executes(bySender((sender, context) -> nights.resume(sender))))
                .then(Commands.literal("end").requires(HOST).executes(bySender((sender, context) -> nights.end(sender))))
                .then(Commands.literal("kick").requires(HOST)
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(bySender((sender, context) -> nights.kick(sender, player(context))))))
                .then(Commands.literal("team").requires(HOST)
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(suggest(nights::teamIds))
                                        .executes(bySender((sender, context) -> nights.team(sender, player(context), string(context, "team")))))));
    }
}
