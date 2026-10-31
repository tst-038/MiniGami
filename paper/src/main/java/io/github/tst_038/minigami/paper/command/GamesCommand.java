package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.MOD;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.player;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.diagnosis.Doctor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.kit.KitAdmin;
import io.github.tst_038.minigami.paper.match.MatchAdmin;
import io.github.tst_038.minigami.paper.match.PublicMatches;
import io.github.tst_038.minigami.paper.match.RunningMatches;
import io.github.tst_038.minigami.paper.night.Nights;
import io.github.tst_038.minigami.paper.schedule.Schedules;
import io.github.tst_038.minigami.paper.party.PartyService;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.github.tst_038.minigami.paper.stats.StatsView;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The {@code /mg} command. Every part follows one pattern: {@code /mg <thing> create …} and
 * {@code /mg <thing> <id> <action>}; typing a part without the rest lists what can follow.
 * Staff parts are hidden from players without the rank they need: {@code minigami.helper}, {@code .mod} or {@code .admin}.
 */
public final class GamesCommand {

    private GamesCommand() {
    }

    private static void joinSelf(Messages messages, PublicMatches publicMatches, CommandContext<CommandSourceStack> context, Optional<String> variant) {
        if (context.getSource().getExecutor() instanceof Player player) {
            publicMatches.join(player, string(context, "game"), variant);
        } else {
            messages.send(context.getSource().getSender(), "join.name-a-player");
        }
    }

    /**
     * @param games ids of the registered games
     * @param features ids of the registered features
     * @param lobby the lobby currently loaded
     * @param reload loads the config again and tells the sender how that went
     */
    public static LiteralCommandNode<CommandSourceStack> create(Messages messages, PublicMatches publicMatches, RunningMatches running, MatchAdmin matches, ConfigEditor editor,
            ArenaSetup arenas, ConfigInfo info, Collection<String> games, Collection<String> features, SnapshotAdmin snapshots, KitAdmin kits, PartyService parties, Nights nights, Schedules schedules, StatsView stats, Doctor doctor, Supplier<Optional<Location>> lobby, Consumer<? super CommandSender> reload) {
        var help = Help.command(messages);
        return Commands.literal("mg")
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> publicMatches.list(sender))))
                .then(Commands.literal("join")
                        .requires(PLAYER.or(ADMIN))
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(suggest(context -> publicMatches.gameIds(context.getSource().getSender())))
                                .executes(bySender((sender, context) -> joinSelf(messages, publicMatches, context, Optional.empty())))
                                // A variant of the game, or for admins a player to put in the game itself; a variant wins a tie.
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests(suggest(context -> publicMatches.variantIds(string(context, "game"))))
                                        .executes(bySender((sender, context) -> {
                                            var game = string(context, "game");
                                            var word = string(context, "variant");
                                            var named = ADMIN.test(context.getSource()) && !publicMatches.hasVariant(game, word)
                                                    ? Optional.ofNullable(sender.getServer().getPlayerExact(word)) : Optional.<Player>empty();
                                            if (named.isPresent()) {
                                                publicMatches.join(sender, named.get(), game, Optional.empty());
                                            } else {
                                                joinSelf(messages, publicMatches, context, Optional.of(word));
                                            }
                                        }))
                                        .then(Commands.argument("player", ArgumentTypes.player())
                                                .requires(ADMIN)
                                                .executes(bySender((sender, context) -> publicMatches.join(sender, player(context),
                                                        string(context, "game"), Optional.of(string(context, "variant")))))))))
                .then(Commands.literal("leave").requires(PLAYER).executes(byPlayer((player, context) -> running.leave(player))))
                .then(StatsCommands.stats(stats, messages))
                .then(StatsCommands.leaderboard(stats))
                .then(Commands.literal("start").requires(MOD).executes(bySender((sender, context) -> running.forceStart(sender))))
                .then(MatchCommands.node(matches, help))
                .then(GameCommands.node(editor, info, help))
                .then(ArenaCommands.node(editor, arenas, info, help))
                .then(GameTypeCommands.game(info, () -> games, help))
                .then(GameTypeCommands.feature(info, () -> features, help))
                .then(ConfigCommands.lobby(editor, lobby, messages, help))
                .then(ConfigCommands.config(editor, info, help))
                .then(PlayerCommands.node(snapshots))
                .then(KitCommands.node(kits, help))
                .then(PartyCommands.node(parties, help))
                .then(NightCommands.node(nights, editor, info, help))
                .then(ScheduleCommands.node(schedules, editor, info, help))
                .then(Commands.literal("reload").requires(ADMIN).executes(bySender((sender, context) -> reload.accept(sender))))
                .then(Commands.literal("doctor").requires(ADMIN).executes(bySender((sender, context) -> doctor.show(sender)))
                        .then(Commands.argument("target", StringArgumentType.word()).suggests(suggest(doctor::targets))
                                .executes(bySender((sender, context) -> doctor.show(sender, string(context, "target"))))))
                .then(Commands.literal("debug").requires(ADMIN).executes(bySender((sender, context) -> running.debug(sender))))
                .build();
    }
}
