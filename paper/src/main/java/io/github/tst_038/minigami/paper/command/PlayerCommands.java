package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

/** {@code /mg player <player> snapshot [restore]}. */
final class PlayerCommands {

    private PlayerCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(SnapshotAdmin snapshots) {
        return Commands.literal("player")
                .requires(ADMIN)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.literal("snapshot")
                                .executes(bySender((sender, context) -> snapshots.inspect(sender, target(context))))
                                .then(Commands.literal("restore")
                                        .executes(bySender((sender, context) -> snapshots.restore(sender, target(context)))))));
    }

    static Player target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst();
    }
}
