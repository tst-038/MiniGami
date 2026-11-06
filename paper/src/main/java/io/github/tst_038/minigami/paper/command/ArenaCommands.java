package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.arena.LootSetup;
import io.github.tst_038.minigami.paper.arena.TerrainSetup;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.game.TeamSetup;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * {@code /mg arena list|create <id>} and {@code /mg arena <id> info|tp|set|unset|delete},
 * {@code spawn list|add [team]|remove <n>|waiting set|remove|spectator set|remove} (also {@code waiting} and {@code spectate} on the arena itself), {@code region list|<name> set|remove|show|schematic [load|paste]}. Points are where the
 * admin stands; regions and maps come from their WorldEdit selection.
 */
final class ArenaCommands {

    private ArenaCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(ConfigEditor editor, ArenaSetup arenas, LootSetup loot, TerrainSetup terrain, ConfigInfo info, Command<CommandSourceStack> help) {
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
                                                        IntegerArgumentType.getInteger(context, "number"))))))
                                .then(spot(editor, help, "waiting", "waiting-spot"))
                                .then(spot(editor, help, "spectator", "spectator-spot")))
                        .then(Commands.literal("region")
                                .executes(help)
                                .then(Commands.literal("list").executes(bySender((sender, context) -> arenas.regions(sender, arena(context)))))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(suggest(context -> arenas.regionNames(arena(context))))
                                        .executes(help)
                                        .then(Commands.literal("set").requires(PLAYER)
                                                .executes(byPlayer((player, context) -> arenas.setRegion(player, arena(context), string(context, "name")))))
                                        .then(Commands.literal("remove")
                                                .executes(bySender((sender, context) -> arenas.removeRegion(sender, arena(context), string(context, "name")))))
                                        .then(Commands.literal("show").requires(PLAYER)
                                                .executes(byPlayer((player, context) -> arenas.showRegion(player, arena(context), string(context, "name")))))
                                        .then(Commands.literal("schematic").requires(PLAYER)
                                                .executes(byPlayer((player, context) -> arenas.saveRegionSchematic(player, arena(context), string(context, "name"))))
                                                .then(Commands.literal("load").requires(PLAYER)
                                                        .executes(byPlayer((player, context) -> arenas.loadRegionSchematic(player, arena(context), string(context, "name")))))
                                                .then(Commands.literal("paste")
                                                        .executes(bySender((sender, context) -> arenas.pasteRegionSchematic(sender, arena(context), string(context, "name"))))))))
                        .then(lootNode(loot, help))
                        .then(terrainNode(terrain, help))
                        .then(Commands.literal("map")
                                .executes(help)
                                .then(Commands.literal("save").requires(PLAYER)
                                        .executes(byPlayer((player, context) -> arenas.saveMap(player, arena(context)))))
                                .then(Commands.literal("reset")
                                        .executes(bySender((sender, context) -> arenas.resetMap(sender, arena(context))))))
                        .then(spot(editor, help, "waiting", "waiting-spot"))
                        .then(spot(editor, help, "spectate", "spectator-spot")));
    }

    /**
     * {@code loot list|show|add <tables>|remove|mark <tables>|mark off|selection add <tables>|selection remove}: the containers of the arena
     * that matches fill, which are the one the admin looks at, those they click, or those in their WorldEdit selection. {@code <tables>} is
     * {@code castle/a}, a mix {@code ice/rare:70 ice/op:30}, and may end in a chance such as {@code 50%}.
     */
    private static ArgumentBuilder<CommandSourceStack, ?> lootNode(LootSetup loot, Command<CommandSourceStack> help) {
        return Commands.literal("loot")
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> loot.list(sender, arena(context)))))
                .then(Commands.literal("show").requires(PLAYER).executes(byPlayer((player, context) -> loot.show(player, arena(context)))))
                .then(Commands.literal("add").requires(PLAYER).then(tables(loot, (player, context) -> loot.add(player, arena(context), string(context, "tables")))))
                .then(Commands.literal("remove").requires(PLAYER).executes(byPlayer((player, context) -> loot.remove(player, arena(context)))))
                .then(Commands.literal("mark").requires(PLAYER)
                        .then(Commands.literal("off").executes(byPlayer((player, context) -> loot.stopMarking(player))))
                        .then(tables(loot, (player, context) -> loot.startMarking(player, arena(context), string(context, "tables")))))
                .then(Commands.literal("selection").requires(PLAYER)
                        .executes(help)
                        .then(Commands.literal("add").then(tables(loot, (player, context) -> loot.addSelection(player, arena(context), string(context, "tables")))))
                        .then(Commands.literal("remove").executes(byPlayer((player, context) -> loot.removeSelection(player, arena(context))))));
    }

    /** {@code terrain preview <group> <style> [seed]}: fills the regions of the group with land, to look at a style. The map reset undoes it. */
    private static ArgumentBuilder<CommandSourceStack, ?> terrainNode(TerrainSetup terrain, Command<CommandSourceStack> help) {
        return Commands.literal("terrain")
                .executes(help)
                .then(Commands.literal("preview")
                        .executes(help)
                        .then(Commands.argument("group", StringArgumentType.word())
                                .then(Commands.argument("style", StringArgumentType.word())
                                        .suggests(suggest(context -> terrain.styleIds()))
                                        .executes(bySender((sender, context) -> terrain.preview(sender, arena(context), string(context, "group"),
                                                string(context, "style"), Optional.empty())))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(bySender((sender, context) -> terrain.preview(sender, arena(context), string(context, "group"),
                                                        string(context, "style"), Optional.of(LongArgumentType.getLong(context, "seed")))))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> tables(LootSetup loot, Nodes.Action<Player> action) {
        return Commands.argument("tables", StringArgumentType.greedyString())
                .suggests(suggest(context -> loot.tableIds()))
                .executes(byPlayer(action));
    }

    /** {@code <literal> set|remove}: a single point of the arena, where the admin stands. */
    private static ArgumentBuilder<CommandSourceStack, ?> spot(ConfigEditor editor, Command<CommandSourceStack> help, String literal, String key) {
        return Commands.literal(literal)
                .executes(help)
                .then(Commands.literal("set")
                        .requires(PLAYER)
                        .executes(byPlayer((player, context) -> editor.set(player, Kind.ARENA, arena(context), key, ConfigLocations.HERE))))
                .then(Commands.literal("remove")
                        .executes(bySender((sender, context) -> editor.unset(sender, Kind.ARENA, arena(context), key))));
    }

    private static String arena(CommandContext<CommandSourceStack> context) {
        return string(context, "arena");
    }
}
