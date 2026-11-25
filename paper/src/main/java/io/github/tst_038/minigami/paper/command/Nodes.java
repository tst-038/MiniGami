package io.github.tst_038.minigami.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.command.PlayerTargets;
import io.github.tst_038.minigami.paper.night.Nights;
import io.github.tst_038.minigami.paper.permission.Staff;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small building blocks shared by the command classes. */
final class Nodes {

    // Staff ranks, each granting the ones below it: admin > mod > helper.
    static final Predicate<CommandSourceStack> ADMIN = source -> source.getSender().hasPermission("minigami.admin");
    /** Runs matches and game nights. */
    static final Predicate<CommandSourceStack> MOD = source -> source.getSender().hasPermission("minigami.mod");
    /** Looks at matches and takes players out of them. */
    static final Predicate<CommandSourceStack> HELPER = source -> source.getSender().hasPermission("minigami.helper");
    /** Who runs game nights; admins can too. */
    static final Predicate<CommandSourceStack> HOST = source -> source.getSender().hasPermission(Nights.HOST_PERMISSION);
    /** Bans players from MiniGami's games, game nights and plugins' scopes; mods can. */
    static final Predicate<CommandSourceStack> BAN = source -> source.getSender().hasPermission("minigami.ban");
    static final Predicate<CommandSourceStack> PLAYER = source -> source.getExecutor() instanceof Player;

    /** What a command does with whoever runs it. */
    interface Action<T> {
        void run(T actor, CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    private Nodes() {
    }

    static Command<CommandSourceStack> bySender(Action<CommandSender> action) {
        return context -> {
            action.run(context.getSource().getSender(), context);
            return Command.SINGLE_SUCCESS;
        };
    }

    /** Only for nodes that require {@link #PLAYER}. */
    static Command<CommandSourceStack> byPlayer(Action<Player> action) {
        return context -> {
            action.run((Player) context.getSource().getExecutor(), context);
            return Command.SINGLE_SUCCESS;
        };
    }

    static String string(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    /** The online player named by the {@code player} argument. */
    static Player player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst();
    }

    /** The online players named by the {@code players} argument, a {@link PlayerTargets#argument()}. */
    static List<Player> players(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return PlayerTargets.resolve(context, "players");
    }

    /**
     * The players a {@code players} argument names, for a command that adds them somewhere: one by name, or those a selector
     * picks who aren't staff, who run the games rather than play them. Empty, having told the sender, when that leaves nobody.
     */
    static Optional<List<Player>> joining(Messages messages, CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var picked = players(context);
        var sender = context.getSource().getSender();
        if (picked.isEmpty()) {
            messages.send(sender, "players.none");
            return Optional.empty();
        }
        if (!isSelector(context, "players")) {
            return Optional.of(picked);
        }
        var joining = picked.stream().filter(player -> !Staff.is(player)).toList();
        if (joining.isEmpty()) {
            messages.send(sender, "join.only-staff-picked", Formatter.number("count", picked.size()));
            return Optional.empty();
        }
        return Optional.of(joining);
    }

    /** Whether the argument was typed as a selector such as {@code @a[distance=..20]} rather than a player's name. */
    static <S> boolean isSelector(CommandContext<S> context, String argument) {
        return context.getNodes().stream().filter(node -> node.getNode().getName().equals(argument)).findFirst()
                .map(node -> node.getRange().get(context.getInput()).startsWith("@")).orElse(false);
    }

    static SuggestionProvider<CommandSourceStack> suggest(Supplier<? extends Collection<String>> ids) {
        return suggest(context -> ids.get());
    }

    static SuggestionProvider<CommandSourceStack> suggest(Function<CommandContext<CommandSourceStack>, ? extends Collection<String>> ids) {
        return (context, builder) -> {
            ids.apply(context).stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }
}
