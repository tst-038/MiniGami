package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.BAN;
import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.players;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.tst_038.minigami.api.command.PlayerTargets;
import io.github.tst_038.minigami.paper.ban.BanAdmin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.bukkit.entity.Player;

/** {@code /mga ban <players> [scope] [length] [reason...]}, {@code /mga unban <players> [scope]} and {@code /mga bans [player]}. */
final class BanCommands {

    /** Lengths to suggest after the scope. */
    private static final List<String> LENGTHS = List.of("30m", "2h", "1d", "7d", "30d");

    private BanCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> ban(BanAdmin bans, Supplier<Set<String>> scopes) {
        return Commands.literal("ban").requires(BAN)
                .then(Commands.argument("players", PlayerTargets.argument())
                        .executes(bySender((sender, context) -> bans.ban(sender, players(context), "")))
                        .then(Commands.argument("details", StringArgumentType.greedyString())
                                .suggests(details(scopes))
                                .executes(bySender((sender, context) -> bans.ban(sender, players(context), string(context, "details"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> unban(BanAdmin bans) {
        return Commands.literal("unban").requires(BAN)
                .then(Commands.argument("players", PlayerTargets.argument())
                        .suggests(suggest(context -> bans.bannedNames()))
                        .executes(bySender((sender, context) -> bans.unban(sender, online(context), typed(context), Optional.empty())))
                        .then(Commands.argument("scope", StringArgumentType.greedyString())
                                .suggests(suggest(context -> bans.scopesOf(typed(context))))
                                .executes(bySender((sender, context) -> bans.unban(sender, online(context), typed(context),
                                        Optional.of(string(context, "scope").strip()))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> list(BanAdmin bans) {
        return Commands.literal("bans").requires(BAN)
                .executes(bySender((sender, context) -> bans.list(sender, Optional.empty())))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(suggest(context -> bans.bannedNames()))
                        .executes(bySender((sender, context) -> bans.list(sender, Optional.of(string(context, "player"))))));
    }

    /** A scope or a length for the first word, a length for the second after a scope, then nothing: the reason is free text. */
    private static SuggestionProvider<CommandSourceStack> details(Supplier<Set<String>> scopes) {
        return (context, builder) -> {
            var typed = builder.getRemaining();
            var words = typed.split(" ", -1);
            var offered = switch (words.length) {
                case 1 -> Stream.concat(scopes.get().stream(), LENGTHS.stream()).toList();
                case 2 -> scopes.get().contains(words[0]) ? LENGTHS : List.<String>of();
                default -> List.<String>of();
            };
            var last = words[words.length - 1];
            var word = builder.createOffset(builder.getStart() + typed.length() - last.length());
            offered.stream().filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT))).forEach(word::suggest);
            return word.buildFuture();
        };
    }

    /** The online players named, or none when the name is of a player who is offline. */
    private static List<Player> online(CommandContext<CommandSourceStack> context) {
        try {
            return players(context);
        } catch (CommandSyntaxException offline) {
            return List.of();
        }
    }

    /** The {@code players} argument as typed, to look an offline player up by. */
    private static String typed(CommandContext<CommandSourceStack> context) {
        return context.getNodes().stream().filter(node -> node.getNode().getName().equals("players")).findFirst()
                .map(node -> node.getRange().get(context.getInput())).orElse("");
    }
}
