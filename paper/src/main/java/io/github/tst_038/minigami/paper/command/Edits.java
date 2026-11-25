package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.bySender;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.api.command.EditCommands;
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
        return EditCommands.set(context -> editor.keys(kind, id.apply(context)), (context, key) -> editor.values(kind, id.apply(context), key),
                (sender, context, key, value) -> editor.set(sender, kind, id.apply(context), key, value));
    }

    static ArgumentBuilder<CommandSourceStack, ?> unset(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return EditCommands.unset(context -> editor.keys(kind, id.apply(context)), (sender, context, key) -> editor.unset(sender, kind, id.apply(context), key));
    }

    static ArgumentBuilder<CommandSourceStack, ?> delete(ConfigEditor editor, Kind kind, Function<CommandContext<CommandSourceStack>, String> id) {
        return Commands.literal("delete").executes(bySender((sender, context) -> editor.delete(sender, kind, id.apply(context))));
    }
}
