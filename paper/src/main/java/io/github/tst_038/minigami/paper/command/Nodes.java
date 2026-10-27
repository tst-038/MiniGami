package io.github.tst_038.minigami.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small building blocks shared by the command classes. */
final class Nodes {

    static final String ADMIN_PERMISSION = "minigami.admin";

    static final Predicate<CommandSourceStack> ADMIN = source -> source.getSender().hasPermission(ADMIN_PERMISSION);
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

    static SuggestionProvider<CommandSourceStack> suggest(Supplier<? extends Collection<String>> ids) {
        return suggest(context -> ids.get());
    }

    static SuggestionProvider<CommandSourceStack> suggest(Function<CommandContext<CommandSourceStack>, ? extends Collection<String>> ids) {
        return (context, builder) -> {
            ids.apply(context).stream().filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }
}
