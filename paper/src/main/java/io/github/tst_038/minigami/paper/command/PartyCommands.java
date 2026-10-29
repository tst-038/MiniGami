package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.player;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.paper.party.PartyService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.List;
import org.bukkit.entity.Player;

/** {@code /mg party invite <player>|accept <leader>|leave|kick <player>|disband|list}: players who stay together between matches. */
final class PartyCommands {

    private PartyCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> node(PartyService parties, Command<CommandSourceStack> help) {
        return Commands.literal("party")
                .requires(PLAYER)
                .executes(help)
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(byPlayer((player, context) -> parties.invite(player, player(context))))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("leader", StringArgumentType.word())
                                .suggests(suggest(context -> context.getSource().getExecutor() instanceof Player player
                                        ? parties.inviters(player.getUniqueId()) : List.of()))
                                .executes(byPlayer((player, context) -> parties.accept(player, string(context, "leader"))))))
                .then(Commands.literal("leave").executes(byPlayer((player, context) -> parties.leave(player))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(suggest(context -> context.getSource().getExecutor() instanceof Player player
                                        ? parties.otherMembers(player.getUniqueId()) : List.of()))
                                .executes(byPlayer((player, context) -> parties.kick(player, string(context, "player"))))))
                .then(Commands.literal("disband").executes(byPlayer((player, context) -> parties.disband(player))))
                .then(Commands.literal("list").executes(byPlayer((player, context) -> parties.list(player))));
    }
}
