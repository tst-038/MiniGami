package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.HELPER;
import static io.github.tst_038.minigami.paper.command.Nodes.MOD;
import static io.github.tst_038.minigami.paper.command.Nodes.player;
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
import io.github.tst_038.minigami.paper.schedule.Schedule;
import io.github.tst_038.minigami.paper.schedule.Schedules;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.command.CommandSender;

/**
 * {@code /mg schedule join [schedule] [player]} for players (admins can name another player), {@code list} for helpers,
 * {@code now|next [game] [variant]} and {@code start|skip|cancel|pause|resume} for mods,
 * and {@code info|create|delete|edit} of the schedule files for admins.
 */
final class ScheduleCommands {

    private ScheduleCommands() {
    }

    /** {@code /mg schedule join [schedule]}, for players. */
    static ArgumentBuilder<CommandSourceStack, ?> forPlayers(Schedules schedules, Command<CommandSourceStack> help) {
        return Commands.literal("schedule")
                .executes(help)
                .then(Commands.literal("join")
                        .requires(PLAYER.and(source -> source.getSender().hasPermission(Schedules.JOIN_PERMISSION) || ADMIN.test(source)))
                        .executes(byPlayer((player, context) -> schedules.join(player, Optional.empty())))
                        .then(Commands.argument("schedule", StringArgumentType.word())
                                .suggests(suggest(schedules::ids))
                                .executes(byPlayer((player, context) -> schedules.join(player, Optional.of(schedule(context)))))));
    }

    /** {@code /mga schedule …}, for staff; {@code join} also names a player to put in the call, for admins. */
    static ArgumentBuilder<CommandSourceStack, ?> node(Schedules schedules, ConfigEditor editor, ConfigInfo info, Command<CommandSourceStack> help) {
        return Commands.literal("schedule")
                .executes(help)
                .then(Commands.literal("join")
                        .requires(ADMIN)
                        .then(Commands.argument("schedule", StringArgumentType.word())
                                .suggests(suggest(schedules::ids))
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(bySender((sender, context) -> schedules.join(sender, player(context), Optional.of(schedule(context))))))))
                .then(Commands.literal("list").requires(HELPER).executes(bySender((sender, context) -> schedules.list(sender))))
                .then(picking("now", schedules, schedules::now))
                .then(picking("next", schedules, schedules::next))
                .then(running("start", schedules, schedules::start))
                .then(running("skip", schedules, schedules::skip))
                .then(running("cancel", schedules, schedules::cancel))
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

    /** A command on a loaded schedule and, optionally, a game and its variant, for mods. */
    private static ArgumentBuilder<CommandSourceStack, ?> picking(String name, Schedules schedules, Picking action) {
        return Commands.literal(name).requires(MOD)
                .then(Commands.argument("schedule", StringArgumentType.word())
                        .suggests(suggest(schedules::ids))
                        .executes(bySender((sender, context) -> action.run(sender, schedule(context), Optional.empty())))
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(suggest(schedules::gameIds))
                                .executes(bySender((sender, context) -> action.run(sender, schedule(context),
                                        Optional.of(new Schedule.Entry(string(context, "game"), Optional.empty())))))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests(suggest(context -> schedules.variantIds(string(context, "game"))))
                                        .executes(bySender((sender, context) -> action.run(sender, schedule(context),
                                                Optional.of(new Schedule.Entry(string(context, "game"), Optional.of(string(context, "variant"))))))))));
    }

    private interface Picking {
        void run(CommandSender sender, String schedule, Optional<Schedule.Entry> game);
    }

    /** A schedule file, loaded or not. */
    private static ArgumentBuilder<CommandSourceStack, ?> file(ConfigEditor editor) {
        return Commands.argument("schedule", StringArgumentType.word()).suggests(suggest(() -> editor.ids(Kind.SCHEDULE)));
    }

    private static String schedule(CommandContext<CommandSourceStack> context) {
        return string(context, "schedule");
    }
}
