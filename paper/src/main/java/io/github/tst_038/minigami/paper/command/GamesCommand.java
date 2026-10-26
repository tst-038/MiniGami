package io.github.tst_038.minigami.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.config.LobbySetup;
import io.github.tst_038.minigami.paper.match.MatchService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * {@code /mg join <mode>|leave|start|debug|setlobby}, and for admins
 * {@code /mg arena create <id>} and {@code /mg arena <id> addspawn|removespawn <n>|setwaiting|info}.
 */
public final class GamesCommand {

    public static final String ADMIN_PERMISSION = "minigami.admin";

    private GamesCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(MatchService service, ArenaSetup arenas, LobbySetup lobby) {
        return Commands.literal("mg")
                .then(Commands.literal("join")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(suggest(service::modeIds))
                                .executes(context -> asPlayer(context.getSource(),
                                        player -> service.join(player, StringArgumentType.getString(context, "mode"))))))
                .then(Commands.literal("leave")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .executes(context -> asPlayer(context.getSource(), service::leave)))
                .then(Commands.literal("start")
                        .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                        .executes(context -> {
                            service.forceStart(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("debug")
                        .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                        .executes(context -> {
                            service.debug(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("setlobby")
                        .requires(source -> source.getExecutor() instanceof Player
                                && source.getSender().hasPermission(ADMIN_PERMISSION))
                        .executes(context -> asPlayer(context.getSource(), lobby::setLobby)))
                .then(Commands.literal("arena")
                        .requires(source -> source.getExecutor() instanceof Player
                                && source.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> asPlayer(context.getSource(),
                                                player -> arenas.create(player, StringArgumentType.getString(context, "id"))))))
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(suggest(arenas::ids))
                                .then(arenaAction("addspawn", arenas::addSpawn))
                                .then(Commands.literal("removespawn")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .executes(context -> asPlayer(context.getSource(), player -> arenas.removeSpawn(
                                                        player, arena(context), IntegerArgumentType.getInteger(context, "number"))))))
                                .then(arenaAction("setwaiting", arenas::setWaitingSpot))
                                .then(arenaAction("info", arenas::info))))
                .build();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> arenaAction(String name, BiConsumer<Player, String> action) {
        return Commands.literal(name).executes(context -> asPlayer(context.getSource(), player -> action.accept(player, arena(context))));
    }

    private static String arena(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "arena");
    }

    private static SuggestionProvider<CommandSourceStack> suggest(Supplier<? extends Collection<String>> ids) {
        return (context, builder) -> {
            ids.get().stream().filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static int asPlayer(CommandSourceStack source, Consumer<Player> action) {
        action.accept((Player) source.getExecutor());
        return Command.SINGLE_SUCCESS;
    }
}
