package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.HELPER;
import static io.github.tst_038.minigami.paper.command.Nodes.MOD;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.paper.schedule.Schedules;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /mg schedule join} for players, {@code list} for helpers, and {@code <id> now|pause|resume} for mods. */
final class ScheduleCommands {

    private ScheduleCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(Schedules schedules, Command<CommandSourceStack> help) {
        return Commands.literal("schedule")
                .executes(help)
                .then(Commands.literal("join")
                        .requires(PLAYER.and(source -> source.getSender().hasPermission(Schedules.JOIN_PERMISSION)))
                        .executes(byPlayer((player, context) -> schedules.join(player))))
                .then(Commands.literal("list").requires(HELPER).executes(bySender((sender, context) -> schedules.list(sender))))
                .then(Commands.argument("schedule", StringArgumentType.word())
                        .requires(MOD)
                        .suggests(suggest(schedules::ids))
                        .executes(help)
                        .then(Commands.literal("now").executes(bySender((sender, context) -> schedules.now(sender, string(context, "schedule")))))
                        .then(Commands.literal("pause").executes(bySender((sender, context) -> schedules.pause(sender, string(context, "schedule")))))
                        .then(Commands.literal("resume").executes(bySender((sender, context) -> schedules.resume(sender, string(context, "schedule"))))));
    }
}
