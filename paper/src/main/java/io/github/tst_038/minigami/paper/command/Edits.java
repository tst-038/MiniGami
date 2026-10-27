package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.bySender;
import static io.github.tst_038.minigami.paper.command.Nodes.string;
import static io.github.tst_038.minigami.paper.command.Nodes.suggest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.function.Function;

/** {@code set <key> <value>}, {@code unset <key>} and {@code delete}, the same for every kind of file. */
final class Edits {

    private Edits() {
    }

    /** @param id the file's id from the typed command */
    static ArgumentBuilder<CommandSourceStack, ?> set(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.literal("set")
                .then(key(editor, kind, id)
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .suggests(suggest(context -> editor.values(kind, id.apply(context), string(context, "key"))))
                                .executes(bySender((sender, context) ->
                                        editor.set(sender, kind, id.apply(context), string(context, "key"), string(context, "value"))))));
    }

    static ArgumentBuilder<CommandSourceStack, ?> unset(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.literal("unset")
                .then(key(editor, kind, id)
                        .executes(bySender((sender, context) -> editor.unset(sender, kind, id.apply(context), string(context, "key")))));
    }

    static ArgumentBuilder<CommandSourceStack, ?> delete(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.literal("delete").executes(bySender((sender, context) -> editor.delete(sender, kind, id.apply(context))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> key(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.argument("key", StringArgumentType.word()).suggests(suggest(context -> editor.keys(kind, id.apply(context))));
    }
}
