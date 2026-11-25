package io.github.tst_038.minigami.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.tst_038.minigami.api.config.EditableFile;
import io.github.tst_038.minigami.api.message.MessageEditor;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bukkit.command.CommandSender;

/**
 * Brigadier nodes for editing files by command, the same in every plugin: {@code set <key> <value>}, {@code unset <key>},
 * a whole {@code config info|set|unset} for an {@link EditableFile} and {@code messages set|unset|info} for a
 * {@link MessageEditor}. Keys and values complete from what the plugin offers. Add permissions with {@code requires} on the result.
 */
public final class EditCommands {

    /** Sets a key to the typed value. */
    public interface Setter {
        void set(CommandSender sender, CommandContext<CommandSourceStack> context, String key, String value);
    }

    /** Takes a key out. */
    public interface Unsetter {
        void unset(CommandSender sender, CommandContext<CommandSourceStack> context, String key);
    }

    private EditCommands() {
    }

    /**
     * {@code <literal> info|set|unset} for the file, such as {@code /kotha config}.
     *
     * @param setCommand the full command that sets a key, ending in a space, such as {@code /kotha config set }; {@code info} puts it in the chat
     */
    public static LiteralArgumentBuilder<CommandSourceStack> config(String literal, EditableFile file, String setCommand) {
        return Commands.literal(literal)
                .then(Commands.literal("info").executes(context -> run(() -> file.info(context.getSource().getSender(), setCommand))))
                .then(set(context -> file.keys(), (context, key) -> file.values(key), (sender, context, key, value) -> file.set(sender, key, value)))
                .then(unset(context -> file.keys(), (sender, context, key) -> file.unset(sender, key)));
    }

    /**
     * {@code <literal> set|unset|info <key>} for the plugin's messages, such as {@code /kotha messages}.
     *
     * @param setCommand the full command that sets a text, ending in a space, such as {@code /kotha messages set }
     */
    public static LiteralArgumentBuilder<CommandSourceStack> messages(String literal, MessageEditor editor, String setCommand) {
        return Commands.literal(literal)
                .then(Commands.literal("set").then(key(context -> editor.keys())
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> run(() -> editor.set(context.getSource().getSender(), key(context), StringArgumentType.getString(context, "text")))))))
                .then(Commands.literal("unset").then(key(context -> editor.keys())
                        .executes(context -> run(() -> editor.unset(context.getSource().getSender(), key(context))))))
                .then(Commands.literal("info").then(key(context -> editor.keys())
                        .executes(context -> run(() -> editor.info(context.getSource().getSender(), key(context), setCommand)))));
    }

    /**
     * {@code set <key> <value>}, the value the rest of the command.
     *
     * @param keys the keys to complete
     * @param values the values to suggest for a key; none if anything goes
     */
    public static LiteralArgumentBuilder<CommandSourceStack> set(Function<CommandContext<CommandSourceStack>, ? extends Collection<String>> keys,
            BiFunction<CommandContext<CommandSourceStack>, String, ? extends Collection<String>> values, Setter action) {
        return Commands.literal("set").then(key(keys)
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(suggest(context -> values.apply(context, key(context))))
                        .executes(context -> run(() -> action.set(context.getSource().getSender(), context, key(context),
                                StringArgumentType.getString(context, "value"))))));
    }

    /** {@code unset <key>}. */
    public static LiteralArgumentBuilder<CommandSourceStack> unset(Function<CommandContext<CommandSourceStack>, ? extends Collection<String>> keys,
            Unsetter action) {
        return Commands.literal("unset").then(key(keys)
                .executes(context -> run(() -> action.unset(context.getSource().getSender(), context, key(context)))));
    }

    /** Suggests those of the given texts that start with what was typed, ignoring case. */
    public static SuggestionProvider<CommandSourceStack> suggest(Function<CommandContext<CommandSourceStack>, ? extends Collection<String>> texts) {
        return (context, builder) -> {
            texts.apply(context).stream().filter(text -> text.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> key(Function<CommandContext<CommandSourceStack>, ? extends Collection<String>> keys) {
        return Commands.argument("key", StringArgumentType.word()).suggests(suggest(keys));
    }

    private static String key(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "key");
    }

    private static int run(Runnable action) {
        action.run();
        return Command.SINGLE_SUCCESS;
    }
}
