package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.HELPER;
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
import io.github.tst_038.minigami.paper.arena.LootSetup;
import io.github.tst_038.minigami.paper.arena.TerrainSetup;
import io.github.tst_038.minigami.paper.diagnosis.Doctor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.GameRenamer;
import io.github.tst_038.minigami.paper.info.ConfigInfo;
import io.github.tst_038.minigami.paper.kit.KitAdmin;
import io.github.tst_038.minigami.paper.loot.LootAdmin;
import io.github.tst_038.minigami.paper.match.MatchAdmin;
import io.github.tst_038.minigami.paper.match.PublicMatches;
import io.github.tst_038.minigami.paper.match.RunningMatches;
import io.github.tst_038.minigami.paper.night.Nights;
import io.github.tst_038.minigami.paper.schedule.Schedules;
import io.github.tst_038.minigami.paper.party.PartyService;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.github.tst_038.minigami.paper.stats.PlaceholderGuide;
import io.github.tst_038.minigami.paper.stats.StatsAdmin;
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
 * The two commands. {@code /mga} is what players use: list, join, leave, stats, parties, nights and schedules.
 * {@code /mga} is for staff and follows one pattern: {@code /mga <thing> create …} and {@code /mga <thing> <id> <action>};
 * typing a part without the rest lists what can follow. Its parts are hidden from players without the rank they need:
 * {@code minigami.helper}, {@code .mod} or {@code .admin}.
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

    /** {@code /mga}: everything a player may do. */
    public static LiteralCommandNode<CommandSourceStack> playerRoot(Messages messages, PublicMatches publicMatches, RunningMatches running, PartyService parties, Nights nights,
            Schedules schedules, StatsView stats) {
        var help = Help.command(messages);
        return Commands.literal("mg")
                .executes(help)
                .then(Commands.literal("list").executes(bySender((sender, context) -> publicMatches.list(sender))))
                .then(Commands.literal("join")
                        .requires(PLAYER)
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(suggest(context -> publicMatches.gameIds(context.getSource().getSender())))
                                .executes(bySender((sender, context) -> joinSelf(messages, publicMatches, context, Optional.empty())))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests(suggest(context -> publicMatches.variantIds(string(context, "game"))))
                                        .executes(bySender((sender, context) -> joinSelf(messages, publicMatches, context, Optional.of(string(context, "variant"))))))))
                .then(Commands.literal("leave")
                        .requires(PLAYER)
                        .executes(context -> {
                            if (context.getSource().getExecutor() instanceof Player self) {
                                running.leave(self);
                            }
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        }))
                .then(StatsCommands.stats(stats, messages))
                .then(StatsCommands.leaderboard(stats))
                .then(PartyCommands.node(parties, help))
                .then(NightCommands.forPlayers(nights, help))
                .then(ScheduleCommands.forPlayers(schedules, help))
                .build();
    }

    /**
     * {@code /mga}: everything for staff.
     *
     * @param games ids of the registered games
     * @param lobby the lobby currently loaded
     * @param reload loads the config again and tells the sender how that went
     */
    public static LiteralCommandNode<CommandSourceStack> adminRoot(Messages messages, PublicMatches publicMatches, RunningMatches running, MatchAdmin matches, ConfigEditor editor, GameRenamer gameRenamer,
            ArenaSetup arenas, LootSetup loot, TerrainSetup terrain, ConfigInfo info, Collection<String> games, SnapshotAdmin snapshots, KitAdmin kits, LootAdmin lootAdmin, Nights nights, Schedules schedules, StatsView stats, StatsAdmin statsAdmin, PlaceholderGuide placeholders, Doctor doctor, Supplier<Optional<Location>> lobby, Consumer<? super CommandSender> reload) {
        var help = Help.command(messages);
        return Commands.literal("mga")
                .requires(HELPER)
                .executes(help)
                .then(Commands.literal("join")
                        .requires(ADMIN)
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(suggest(context -> publicMatches.gameIds(context.getSource().getSender())))
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(bySender((sender, context) -> publicMatches.join(sender, player(context), string(context, "game"), Optional.empty())))
                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                .suggests(suggest(context -> publicMatches.variantIds(string(context, "game"))))
                                                .executes(bySender((sender, context) -> publicMatches.join(sender, player(context),
                                                        string(context, "game"), Optional.of(string(context, "variant")))))))))
                .then(Commands.literal("leave")
                        .requires(HELPER)
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(bySender((sender, context) -> running.leave(sender, player(context))))))
                .then(Commands.literal("start").requires(MOD).executes(bySender((sender, context) -> running.forceStart(sender))))
                .then(MatchCommands.node(matches, help))
                .then(GameCommands.node(editor, gameRenamer, info, help))
                .then(ArenaCommands.node(editor, arenas, loot, terrain, info, help))
                .then(GameTypeCommands.game(info, () -> games, help))
                .then(GameTypeCommands.options(info, () -> games))
                .then(ConfigCommands.lobby(editor, lobby, messages, help))
                .then(ConfigCommands.config(editor, info, help))
                .then(PlayerCommands.node(snapshots))
                .then(KitCommands.node(kits, help))
                .then(LootCommands.node(lootAdmin, help))
                .then(NightCommands.node(nights, editor, info, help))
                .then(ScheduleCommands.node(schedules, editor, info, help))
                .then(Commands.literal("reload").requires(ADMIN).executes(bySender((sender, context) -> reload.accept(sender))))
                .then(Commands.literal("doctor").requires(ADMIN).executes(bySender((sender, context) -> doctor.show(sender)))
                        .then(Commands.argument("target", StringArgumentType.word()).suggests(suggest(doctor::targets))
                                .executes(bySender((sender, context) -> doctor.show(sender, string(context, "target"))))))
                .then(Commands.literal("debug").requires(ADMIN).executes(bySender((sender, context) -> running.debug(sender))))
                .then(Commands.literal("placeholders")
                        .executes(bySender((sender, context) -> placeholders.show(sender, Optional.empty())))
                        .then(Commands.argument("game", StringArgumentType.word()).suggests(suggest(context -> stats.gameIds()))
                                .executes(bySender((sender, context) -> placeholders.show(sender, Optional.of(string(context, "game")))))))
                .then(StatsCommands.admin(statsAdmin, stats, messages))
                .build();
    }
}
