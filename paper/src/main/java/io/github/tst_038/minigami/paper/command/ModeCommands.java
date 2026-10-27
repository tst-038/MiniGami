package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /mg mode create <id> <game> <arena>} and {@code /mg mode <id> set|unset|delete}. */
final class ModeCommands {

    private ModeCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(ConfigEditor editor, Command<CommandSourceStack> help) {
        return Commands.literal("mode")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("game", StringArgumentType.word())
                                        .suggests(suggest(editor::gameIds))
                                        .then(Commands.argument("arena", StringArgumentType.word())
                                                .suggests(suggest(() -> editor.ids(Kind.ARENA)))
                                                .executes(bySender((sender, context) -> editor.createMode(sender, string(context, "id"),
                                                        string(context, "game"), string(context, "arena"))))))))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(suggest(() -> editor.ids(Kind.MODE)))
                        .executes(help)
                        .then(Edits.set(editor, Kind.MODE, ModeCommands::mode))
                        .then(Edits.unset(editor, Kind.MODE, ModeCommands::mode))
                        .then(Edits.delete(editor, Kind.MODE, ModeCommands::mode)));
    }

    private static String mode(CommandContext<CommandSourceStack> context) {
        return string(context, "mode");
    }
}
