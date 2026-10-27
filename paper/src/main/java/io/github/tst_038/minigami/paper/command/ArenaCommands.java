package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.mode.TeamSetup;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;

/**
 * {@code /mg arena list|create <id>} and {@code /mg arena <id> info|tp|set|unset|delete},
 * {@code spawn list|add [team]|remove <n>}, {@code waiting set}. Points are where the admin stands.
 */
final class ArenaCommands {

    private ArenaCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(ConfigEditor editor, ArenaSetup arenas, ConfigInfo info, Command<CommandSourceStack> help) {
        return Commands.literal("arena")
                .requires(ADMIN)
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> info.listArenas(sender))))
                .then(Commands.literal("create")
                        .requires(PLAYER)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(byPlayer((player, context) -> editor.createArena(player, string(context, "id"))))))
                .then(Commands.argument("arena", StringArgumentType.word())
                        .suggests(suggest(() -> editor.ids(Kind.ARENA)))
                        .executes(help)
                        .then(Commands.literal("info").executes(bySender((sender, context) -> arenas.info(sender, arena(context)))))
                        .then(Commands.literal("tp").requires(PLAYER).executes(byPlayer((player, context) -> arenas.teleport(player, arena(context)))))
                        .then(Edits.set(editor, Kind.ARENA, ArenaCommands::arena))
                        .then(Edits.unset(editor, Kind.ARENA, ArenaCommands::arena))
                        .then(Edits.delete(editor, Kind.ARENA, ArenaCommands::arena))
                        .then(Commands.literal("spawn")
                                .executes(help)
                                .then(Commands.literal("list").executes(bySender((sender, context) -> arenas.spawns(sender, arena(context)))))
                                .then(Commands.literal("add")
                                        .requires(PLAYER)
                                        .executes(byPlayer((player, context) -> arenas.addSpawn(player, arena(context), Optional.empty())))
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(suggest(() -> TeamSetup.IDS))
                                                .executes(byPlayer((player, context) ->
                                                        arenas.addSpawn(player, arena(context), Optional.of(string(context, "team")))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .executes(bySender((sender, context) -> arenas.removeSpawn(sender, arena(context),
                                                        IntegerArgumentType.getInteger(context, "number")))))))
                        .then(Commands.literal("map")
                                .executes(help)
                                .then(Commands.literal("save").requires(PLAYER)
                                        .executes(byPlayer((player, context) -> arenas.saveMap(player, arena(context)))))
                                .then(Commands.literal("reset")
                                        .executes(bySender((sender, context) -> arenas.resetMap(sender, arena(context))))))
                        .then(Commands.literal("waiting")
                                .executes(help)
                                .then(Commands.literal("set")
                                        .requires(PLAYER)
                                        .executes(byPlayer((player, context) ->
                                                editor.set(player, Kind.ARENA, arena(context), "waiting-spot", ConfigLocations.HERE))))));
    }

    private static String arena(CommandContext<CommandSourceStack> context) {
        return string(context, "arena");
    }
}
