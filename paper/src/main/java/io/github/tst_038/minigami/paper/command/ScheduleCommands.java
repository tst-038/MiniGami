package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
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
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.schedule.Schedules;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.command.CommandSender;

/**
 * {@code /mg schedule join [schedule]} for players (and admins), {@code list} for helpers, {@code now|pause|resume} for mods,
 * and {@code info|create|delete|edit} of the schedule files for admins.
 */
final class ScheduleCommands {

    private ScheduleCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(Schedules schedules, ConfigEditor editor, ConfigInfo info, Command<CommandSourceStack> help) {
        return Commands.literal("schedule")
                .executes(help)
                .then(Commands.literal("join")
                        .requires(PLAYER.and(source -> source.getSender().hasPermission(Schedules.JOIN_PERMISSION) || ADMIN.test(source)))
                        .executes(byPlayer((player, context) -> schedules.join(player, Optional.empty())))
                        .then(Commands.argument("schedule", StringArgumentType.word())
                                .suggests(suggest(schedules::ids))
                                .executes(byPlayer((player, context) -> schedules.join(player, Optional.of(schedule(context)))))))
                .then(Commands.literal("list").requires(HELPER).executes(bySender((sender, context) -> schedules.list(sender))))
                .then(running("now", schedules, schedules::now))
                .then(running("pause", schedules, schedules::pause))
                .then(running("resume", schedules, schedules::resume))
                .then(Commands.literal("info").requires(ADMIN)
                        .executes(bySender((sender, context) -> info.listSchedules(sender)))
                        .then(file(editor).executes(bySender((sender, context) -> info.schedule(sender, schedule(context))))))
                .then(Commands.literal("create").requires(ADMIN)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("game", StringArgumentType.word())
                                        .suggests(suggest(() -> editor.ids(Kind.GAME)))
                                        .then(Commands.argument("when", StringArgumentType.greedyString())
                                                .executes(bySender((sender, context) -> editor.createSchedule(sender, string(context, "id"),
                                                        string(context, "game"), string(context, "when"))))))))
                .then(Commands.literal("delete").requires(ADMIN)
                        .then(file(editor).executes(bySender((sender, context) -> editor.delete(sender, Kind.SCHEDULE, schedule(context))))))
                .then(Commands.literal("edit").requires(ADMIN)
                        .executes(help)
                        .then(file(editor)
                                .executes(help)
                                .then(Edits.set(editor, Kind.SCHEDULE, ScheduleCommands::schedule))
                                .then(Edits.unset(editor, Kind.SCHEDULE, ScheduleCommands::schedule))));
    }

    /** A command on a loaded schedule, for mods. */
    private static ArgumentBuilder<CommandSourceStack, ?> running(String name, Schedules schedules, BiConsumer<CommandSender, String> action) {
        return Commands.literal(name).requires(MOD)
                .then(Commands.argument("schedule", StringArgumentType.word())
                        .suggests(suggest(schedules::ids))
                        .executes(bySender((sender, context) -> action.accept(sender, schedule(context)))));
    }

    /** A schedule file, loaded or not. */
    private static ArgumentBuilder<CommandSourceStack, ?> file(ConfigEditor editor) {
        return Commands.argument("schedule", StringArgumentType.word()).suggests(suggest(() -> editor.ids(Kind.SCHEDULE)));
    }

    private static String schedule(CommandContext<CommandSourceStack> context) {
        return string(context, "schedule");
    }
}
