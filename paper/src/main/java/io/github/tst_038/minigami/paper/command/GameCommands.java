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

/** {@code /mg game list|create <id> <gametype> <arena>} and {@code /mg game <id> info|set|unset|delete|variant ...}. */
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
                        .then(Edits.delete(editor, Kind.GAME, GameCommands::game))
                        .then(variant(editor, help)));
    }

    /** {@code variant create <variant> <name>} and {@code variant <variant> set|unset|delete}. */
    private static ArgumentBuilder<CommandSourceStack, ?> variant(ConfigEditor editor, Command<CommandSourceStack> help) {
        var key = Commands.argument("key", StringArgumentType.word()).suggests(suggest(context -> editor.variantKeys(game(context))));
        return Commands.literal("variant")
                .executes(help)
                .then(Commands.literal("create")
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(bySender((sender, context) -> editor.createVariant(sender, game(context),
                                                variant(context), string(context, "name")))))))
                .then(Commands.argument("variant", StringArgumentType.word())
                        .suggests(suggest(context -> editor.variantIds(game(context))))
                        .executes(help)
                        .then(Commands.literal("set")
                                .then(key.then(Commands.argument("value", StringArgumentType.greedyString())
                                        .suggests(suggest(context -> editor.values(Kind.GAME, game(context), string(context, "key"))))
                                        .executes(bySender((sender, context) -> editor.setVariant(sender, game(context), variant(context),
                                                string(context, "key"), string(context, "value")))))))
                        .then(Commands.literal("unset")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(suggest(context -> editor.variantKeys(game(context))))
                                        .executes(bySender((sender, context) -> editor.unsetVariant(sender, game(context), variant(context),
                                                string(context, "key"))))))
                        .then(Commands.literal("delete")
                                .executes(bySender((sender, context) -> editor.deleteVariant(sender, game(context), variant(context))))));
    }

    private static String variant(CommandContext<CommandSourceStack> context) {
        return string(context, "variant");
    }

    private static String game(CommandContext<CommandSourceStack> context) {
        return string(context, "game");
    }
}
