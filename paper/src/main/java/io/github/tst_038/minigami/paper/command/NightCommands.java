package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.HOST;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.player;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.gamenight.template.PlaylistEdit;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.night.Nights;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.List;
import java.util.function.Function;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * {@code /mg night join|leave|standings} for players, and for hosts
 * {@code start <night>|begin|next|skip|pause|resume|end|kick <player>|team <player> <team>}. For admins, the
 * templates: {@code list|create <id> <game> [name]|info <id>|delete <id>} and {@code edit <id> set|unset|playlist ...}, where
 * {@code playlist} is {@code list|add <game> [variant]|remove <n>|move <n> <to>|points <n> <place> <points>|taking-part <n> <points>}.
 */
final class NightCommands {

    private NightCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(Nights nights, ConfigEditor editor, ConfigInfo info, Command<CommandSourceStack> help) {
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
                .then(Commands.literal("list").requires(ADMIN).executes(bySender((sender, context) -> info.listNights(sender))))
                .then(Commands.literal("info").requires(ADMIN)
                        .then(night(editor).executes(bySender((sender, context) -> info.night(sender, night(context))))))
                .then(Commands.literal("create").requires(ADMIN)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("game", StringArgumentType.word())
                                        .suggests(suggest(() -> editor.ids(Kind.GAME)))
                                        .executes(bySender((sender, context) -> editor.createNight(sender, string(context, "id"),
                                                string(context, "game"), string(context, "id"))))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(bySender((sender, context) -> editor.createNight(sender, string(context, "id"),
                                                        string(context, "game"), string(context, "name"))))))))
                .then(Commands.literal("delete").requires(ADMIN)
                        .then(night(editor).executes(bySender((sender, context) -> editor.delete(sender, Kind.NIGHT, night(context))))))
                .then(Commands.literal("edit").requires(ADMIN)
                        .executes(help)
                        .then(night(editor)
                                .executes(help)
                                .then(Edits.set(editor, Kind.NIGHT, NightCommands::night))
                                .then(Edits.unset(editor, Kind.NIGHT, NightCommands::night))
                                .then(playlist(editor, info, help))))
                .then(Commands.literal("team").requires(HOST)
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(suggest(nights::teamIds))
                                        .executes(bySender((sender, context) -> nights.team(sender, player(context), string(context, "team")))))));
    }

    /** {@code playlist list|add <game> [variant]|remove <n>|move <n> <to>|points <n> <place> <points>|taking-part <n> <points>}. */
    private static ArgumentBuilder<CommandSourceStack, ?> playlist(ConfigEditor editor, ConfigInfo info, Command<CommandSourceStack> help) {
        return Commands.literal("playlist")
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> info.nightPlaylist(sender, night(context)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(suggest(() -> editor.ids(Kind.GAME)))
                                .executes(bySender((sender, context) -> editPlaylist(editor, sender, context,
                                        list -> PlaylistEdit.add(list, string(context, "game"), Optional.empty()), "edit.playlist-added",
                                        Placeholder.unparsed("game", string(context, "game")))))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests(suggest(context -> editor.variantIds(string(context, "game"))))
                                        .executes(bySender((sender, context) -> editPlaylist(editor, sender, context,
                                                list -> PlaylistEdit.add(list, string(context, "game"), Optional.of(string(context, "variant"))),
                                                "edit.playlist-added", Placeholder.unparsed("game", string(context, "game"))))))))
                .then(Commands.literal("remove")
                        .then(position("n").executes(bySender((sender, context) -> editPlaylist(editor, sender, context,
                                list -> PlaylistEdit.remove(list, integer(context, "n")), "edit.playlist-removed", number(context, "n"))))))
                .then(Commands.literal("move")
                        .then(position("n")
                                .then(position("to").executes(bySender((sender, context) -> editPlaylist(editor, sender, context,
                                        list -> PlaylistEdit.move(list, integer(context, "n"), integer(context, "to")), "edit.playlist-moved",
                                        number(context, "n"), number(context, "to")))))))
                .then(Commands.literal("points")
                        .then(position("n")
                                .then(Commands.argument("place", IntegerArgumentType.integer(1))
                                        .then(points().executes(bySender((sender, context) -> editPlaylist(editor, sender, context,
                                                list -> PlaylistEdit.setPlace(list, integer(context, "n"), integer(context, "place"), integer(context, "points")),
                                                "edit.playlist-points", number(context, "n"))))))))
                .then(Commands.literal("taking-part")
                        .then(position("n")
                                .then(points().executes(bySender((sender, context) -> editPlaylist(editor, sender, context,
                                        list -> PlaylistEdit.setTakingPart(list, integer(context, "n"), integer(context, "points")),
                                        "edit.playlist-points", number(context, "n")))))));
    }

    /** Checks and saves the night's playlist as changed; {@code done} gets the given placeholders and {@code <file>}. */
    private static void editPlaylist(ConfigEditor editor, CommandSender sender, CommandContext<CommandSourceStack> context,
            Function<List<?>, List<Object>> change, String done, TagResolver... placeholders) {
        editor.change(sender, Kind.NIGHT, night(context), config -> config.set(PlaylistEdit.KEY, change.apply(config.getList(PlaylistEdit.KEY, List.of()))),
                done, placeholders);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> night(ConfigEditor editor) {
        return Commands.argument("night", StringArgumentType.word()).suggests(suggest(() -> editor.ids(Kind.NIGHT)));
    }

    /** A place in the playlist, counted from 1. */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> position(String name) {
        return Commands.argument(name, IntegerArgumentType.integer(1));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> points() {
        return Commands.argument("points", IntegerArgumentType.integer(0));
    }

    private static int integer(CommandContext<CommandSourceStack> context, String name) {
        return IntegerArgumentType.getInteger(context, name);
    }

    private static TagResolver number(CommandContext<CommandSourceStack> context, String name) {
        return Formatter.number(name, integer(context, name));
    }

    private static String night(CommandContext<CommandSourceStack> context) {
        return string(context, "night");
    }
}
