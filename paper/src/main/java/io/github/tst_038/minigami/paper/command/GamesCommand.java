package io.github.tst_038.minigami.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.tst_038.minigami.paper.match.MatchService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** {@code /mg join <mode>|leave|start|debug}. */
public final class GamesCommand {

    public static final String ADMIN_PERMISSION = "minigami.admin";

    private GamesCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(MatchService service) {
        return Commands.literal("mg")
                .then(Commands.literal("join")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    service.modeIds().stream()
                                            .filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
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
                .build();
    }

    private static int asPlayer(CommandSourceStack source, Consumer<Player> action) {
        action.accept((Player) source.getExecutor());
        return Command.SINGLE_SUCCESS;
    }
}
