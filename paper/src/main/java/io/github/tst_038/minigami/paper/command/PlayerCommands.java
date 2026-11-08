package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.player;

import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

/** {@code /mga player <player> snapshot [restore]}. */
final class PlayerCommands {

    private PlayerCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(SnapshotAdmin snapshots) {
        return Commands.literal("player")
                .requires(ADMIN)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.literal("snapshot")
                                .executes(bySender((sender, context) -> snapshots.inspect(sender, player(context))))
                                .then(Commands.literal("restore")
                                        .executes(bySender((sender, context) -> snapshots.restore(sender, player(context)))))));
    }
}
