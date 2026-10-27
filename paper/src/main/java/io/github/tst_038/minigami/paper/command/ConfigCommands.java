package io.github.tst_038.minigami.paper.command;

import static io.github.tst_038.minigami.paper.command.Nodes.ADMIN;
import static io.github.tst_038.minigami.paper.command.Nodes.PLAYER;
import static io.github.tst_038.minigami.paper.command.Nodes.byPlayer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Location;

/** {@code /mg config set|unset}, and {@code /mg lobby set|tp} for the one setting admins change most. */
final class ConfigCommands {

    private ConfigCommands() {
    }

    static ArgumentBuilder<CommandSourceStack, ?> config(ConfigEditor editor, Command<CommandSourceStack> help) {
        return Commands.literal("config")
                .requires(ADMIN)
                .executes(help)
                .then(Edits.set(editor, Kind.CONFIG, context -> ""))
                .then(Edits.unset(editor, Kind.CONFIG, context -> ""));
    }

    /** @param lobby the lobby currently loaded */
    static ArgumentBuilder<CommandSourceStack, ?> lobby(ConfigEditor editor, Supplier<Optional<Location>> lobby, Messages messages,
            Command<CommandSourceStack> help) {
        return Commands.literal("lobby")
                .requires(ADMIN.and(PLAYER))
                .executes(help)
                .then(Commands.literal("set").executes(byPlayer((player, context) ->
                        editor.set(player, Kind.CONFIG, "", "lobby", ConfigLocations.HERE))))
                .then(Commands.literal("tp").executes(byPlayer((player, context) -> lobby.get().ifPresentOrElse(
                        player::teleportAsync, () -> messages.send(player, "lobby.none")))));
    }
}
