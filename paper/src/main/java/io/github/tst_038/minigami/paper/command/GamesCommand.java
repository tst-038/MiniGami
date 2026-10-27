package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.match.MatchService;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

/**
 * The {@code /mg} command. Every part follows one pattern: {@code /mg <thing> create …} and
 * {@code /mg <thing> <id> <action>}; typing a part without the rest lists what can follow.
 * Admin parts are hidden from players without {@code minigami.admin}.
 */
public final class GamesCommand {

    private GamesCommand() {
    }

    /**
     * @param games ids of the registered games
     * @param features ids of the registered features
     * @param lobby the lobby currently loaded
     * @param reload loads the config again and tells the sender how that went
     */
    public static LiteralCommandNode<CommandSourceStack> create(Messages messages, MatchService service, ConfigEditor editor,
            ArenaSetup arenas, ConfigInfo info, Collection<String> games, Collection<String> features, SnapshotAdmin snapshots, Supplier<Optional<Location>> lobby, Consumer<? super CommandSender> reload) {
        var help = Help.command(messages);
        return Commands.literal("mg")
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> service.list(sender))))
                .then(Commands.literal("join")
                        .requires(PLAYER)
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(suggest(service::modeIds))
                                .executes(byPlayer((player, context) -> service.join(player, string(context, "mode"))))))
                .then(Commands.literal("leave").requires(PLAYER).executes(byPlayer((player, context) -> service.leave(player))))
                .then(Commands.literal("start").requires(ADMIN).executes(bySender((sender, context) -> service.forceStart(sender))))
                .then(ModeCommands.node(editor, info, help))
                .then(ArenaCommands.node(editor, arenas, info, help))
                .then(GameCommands.game(info, () -> games, help))
                .then(GameCommands.feature(info, () -> features, help))
                .then(ConfigCommands.lobby(editor, lobby, messages, help))
                .then(ConfigCommands.config(editor, info, help))
                .then(PlayerCommands.node(snapshots))
                .then(Commands.literal("reload").requires(ADMIN).executes(bySender((sender, context) -> reload.accept(sender))))
                .then(Commands.literal("debug").requires(ADMIN).executes(bySender((sender, context) -> service.debug(sender))))
                .build();
    }
}
