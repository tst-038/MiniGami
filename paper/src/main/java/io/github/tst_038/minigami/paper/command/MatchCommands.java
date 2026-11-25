package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.HELPER;
import static io.github.tst_038.minigami.paper.command.Nodes.MOD;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.joining;
import static io.github.tst_038.minigami.paper.command.Nodes.players;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.command.PlayerTargets;
import io.github.tst_038.minigami.paper.match.MatchAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /mga match list} and {@code /mga match <id> info|start|end|pause|resume|stop|phase <phase>|add <players>|kick <players>}; players by name or selector. */
final class MatchCommands {

    private MatchCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(Messages messages, MatchAdmin matches, Command<CommandSourceStack> help) {
        return Commands.literal("match")
                .requires(HELPER)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> matches.list(sender))))
                .then(Commands.argument("match", StringArgumentType.word())
                        .suggests(suggest(matches::ids))
                        .executes(help)
                        .then(Commands.literal("info").executes(bySender((sender, context) -> matches.info(sender, match(context)))))
                        .then(Commands.literal("start").requires(MOD).executes(bySender((sender, context) -> matches.start(sender, match(context)))))
                        .then(Commands.literal("end").requires(MOD).executes(bySender((sender, context) -> matches.end(sender, match(context)))))
                        .then(Commands.literal("phase").requires(MOD)
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .suggests(suggest(context -> matches.laterPhases(match(context))))
                                        .executes(bySender((sender, context) -> matches.phase(sender, match(context), string(context, "phase"))))))
                        .then(Commands.literal("pause").requires(MOD).executes(bySender((sender, context) -> matches.pause(sender, match(context)))))
                        .then(Commands.literal("resume").requires(MOD).executes(bySender((sender, context) -> matches.resume(sender, match(context)))))
                        .then(Commands.literal("stop").requires(MOD).executes(bySender((sender, context) -> matches.stop(sender, match(context)))))
                        .then(Commands.literal("add").requires(MOD)
                                .then(Commands.argument("players", PlayerTargets.argument())
                                        .executes(bySender((sender, context) -> joining(messages, context).ifPresent(players -> matches.add(sender, match(context), players))))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("players", PlayerTargets.argument())
                                        .executes(bySender((sender, context) -> matches.kick(sender, match(context), players(context)))))));
    }

    private static String match(CommandContext<CommandSourceStack> context) {
        return string(context, "match");
    }
}
