package io.github.tst_038.minigami.api.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.tst_038.minigami.api.message.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.function.BiPredicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Commands that act on several players at once, named by a name or a selector such as {@code @a[distance=..10]}: take the
 * argument from {@link #argument()}, read it with {@link #resolve}, and act with {@link #each}, which tells the sender one
 * thing however many players there were.
 */
public final class PlayerTargets {

    private PlayerTargets() {
    }

    /** One or more online players, by name or selector. */
    public static ArgumentType<PlayerSelectorArgumentResolver> argument() {
        return ArgumentTypes.players();
    }

    /** The online players the argument {@code name} names; may be empty, as for a selector that matches nobody. */
    public static List<Player> resolve(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(context.getSource());
    }

    /**
     * Runs the action for each player, or for anything else a command names several of, such as offline players. With one, the
     * action talks to the sender itself, so they hear the detail; with several it is given a silent audience and the sender hears {@code summary} once, with {@code %count%} (how many the action
     * did) and {@code %total%} (how many were named) on top of {@code tags}. With none, the sender hears {@code none}.
     *
     * @param action does it for one player, telling the audience why not; returns whether it did
     */
    public static <T> void each(Messages messages, Audience sender, List<T> players, BiPredicate<Audience, ? super T> action,
            String none, String summary, TagResolver... tags) {
        switch (players.size()) {
            case 0 -> messages.send(sender, none);
            case 1 -> action.test(sender, players.getFirst());
            default -> {
                var count = players.stream().filter(player -> action.test(Audience.empty(), player)).count();
                messages.send(sender, summary, TagResolver.resolver(TagResolver.resolver(tags), Formatter.number("count", count),
                        Formatter.number("total", players.size())));
            }
        }
    }
}
