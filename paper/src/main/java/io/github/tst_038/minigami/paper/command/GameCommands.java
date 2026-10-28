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
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/** {@code /mg game list|create <id> <gametype> <arena>} and {@code /mg game <id> info|set|unset|delete}. */
final class GameCommands {

    private GameCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(ConfigEditor editor, ConfigInfo info, Command<CommandSourceStack> help) {
        return Commands.literal("game")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> info.listGames(sender))))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("gametype", StringArgumentType.word())
                                        .suggests(suggest(editor::gametypeIds))
                                        .then(Commands.argument("arena", StringArgumentType.word())
                                                .suggests(suggest(() -> editor.ids(Kind.ARENA)))
                                                .executes(bySender((sender, context) -> editor.createGame(sender, string(context, "id"),
                                                        string(context, "gametype"), string(context, "arena"))))))))
                .then(Commands.argument("game", StringArgumentType.word())
                        .suggests(suggest(() -> editor.ids(Kind.GAME)))
                        .executes(help)
                        .then(Commands.literal("info").executes(bySender((sender, context) -> info.game(sender, game(context)))))
                        .then(Edits.set(editor, Kind.GAME, GameCommands::game))
                        .then(Edits.unset(editor, Kind.GAME, GameCommands::game))
                        .then(Edits.delete(editor, Kind.GAME, GameCommands::game)));
    }

    private static String game(CommandContext<CommandSourceStack> context) {
        return string(context, "game");
    }
}
