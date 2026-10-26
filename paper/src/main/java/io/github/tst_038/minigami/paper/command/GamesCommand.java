package io.github.tst_038.minigami.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.tst_038.minigami.paper.arena.ArenaSetup;
import io.github.tst_038.minigami.paper.config.LobbySetup;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.match.MatchService;
import io.github.tst_038.minigami.paper.session.SnapshotAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /mg join <mode>|leave|start|debug|reload|setlobby}, and for admins
 * {@code /mg arena create <id>}, {@code /mg arena <id> addspawn|removespawn <n>|setwaiting|info},
 * {@code /mg mode create <id> <game> <arena>}, {@code /mg arena|mode <id> set <key> <value>|unset <key>|delete},
 * {@code /mg config set <key> <value>|unset <key>} and {@code /mg snapshot <player> [restore]}.
 */
public final class GamesCommand {

    public static final String ADMIN_PERMISSION = "minigami.admin";

    private GamesCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(MatchService service, ArenaSetup arenas, LobbySetup lobby,
            SnapshotAdmin snapshots, ConfigEditor editor, Consumer<? super CommandSender> reload) {
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
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                        .executes(context -> {
                            reload.accept(context.getSource().getSender());
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
                                .suggests(suggest(() -> editor.ids(Kind.ARENA)))
                                .then(set(editor, Kind.ARENA, GamesCommand::arena))
                                .then(unset(editor, Kind.ARENA, GamesCommand::arena))
                                .then(delete(editor, Kind.ARENA, GamesCommand::arena))
                                .then(arenaAction("addspawn", arenas::addSpawn))
                                .then(Commands.literal("removespawn")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .executes(context -> asPlayer(context.getSource(), player -> arenas.removeSpawn(
                                                        player, arena(context), IntegerArgumentType.getInteger(context, "number"))))))
                                .then(arenaAction("setwaiting", arenas::setWaitingSpot))
                                .then(arenaAction("info", arenas::info))))
                .then(Commands.literal("mode")
                        .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("game", StringArgumentType.word())
                                                .suggests(suggest(editor::gameIds))
                                                .then(Commands.argument("arena", StringArgumentType.word())
                                                        .suggests(suggest(() -> editor.ids(Kind.ARENA)))
                                                        .executes(context -> {
                                                            editor.createMode(context.getSource().getSender(),
                                                                    StringArgumentType.getString(context, "id"),
                                                                    StringArgumentType.getString(context, "game"), arena(context));
                                                            return Command.SINGLE_SUCCESS;
                                                        })))))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(suggest(() -> editor.ids(Kind.MODE)))
                                .then(set(editor, Kind.MODE, GamesCommand::mode))
                                .then(unset(editor, Kind.MODE, GamesCommand::mode))
                                .then(delete(editor, Kind.MODE, GamesCommand::mode))))
                .then(Commands.literal("config")
                        .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(set(editor, Kind.CONFIG, context -> ""))
                        .then(unset(editor, Kind.CONFIG, context -> "")))
                .then(Commands.literal("snapshot")
                        .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(context -> {
                                    snapshots.inspect(context.getSource().getSender(), target(context));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("restore")
                                        .executes(context -> {
                                            snapshots.restore(context.getSource().getSender(), target(context));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> arenaAction(String name, BiConsumer<Player, String> action) {
        return Commands.literal(name).executes(context -> asPlayer(context.getSource(), player -> action.accept(player, arena(context))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> set(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.literal("set")
                .then(key(editor, kind, id)
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .suggests(suggest(context -> editor.values(kind, id.apply(context), key(context))))
                                .executes(context -> {
                                    editor.set(context.getSource().getSender(), kind, id.apply(context), key(context),
                                            StringArgumentType.getString(context, "value"));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> unset(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.literal("unset")
                .then(key(editor, kind, id)
                        .executes(context -> {
                            editor.unset(context.getSource().getSender(), kind, id.apply(context), key(context));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> delete(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.literal("delete")
                .executes(context -> {
                    editor.delete(context.getSource().getSender(), kind, id.apply(context));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> key(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.argument("key", StringArgumentType.word())
                .suggests(suggest(context -> editor.keys(kind, id.apply(context))));
    }

    private static String key(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "key");
    }

    private static String mode(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "mode");
    }

    private static Player target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst();
    }

    private static String arena(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "arena");
    }

    private static SuggestionProvider<CommandSourceStack> suggest(Supplier<? extends Collection<String>> ids) {
        return suggest(context -> ids.get());
    }

    private static SuggestionProvider<CommandSourceStack> suggest(Function<CommandContext<CommandSourceStack>, ? extends Collection<String>> ids) {
        return (context, builder) -> {
            ids.apply(context).stream().filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static int asPlayer(CommandSourceStack source, Consumer<Player> action) {
        action.accept((Player) source.getExecutor());
        return Command.SINGLE_SUCCESS;
    }
}
