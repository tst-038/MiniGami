package io.github.tst_038.minigami.api.reward;

import io.github.tst_038.minigami.api.config.ConfigReader;
import org.bukkit.entity.Player;

/**
 * Runs a command from the console, with {@code %player%} as the winner's name, such as an economy
 * plugin's {@code eco give %player% 100}. A command that is unknown or fails is a {@link RewardFailedException}.
 *
 * @param command without the leading slash
 */
record CommandReward(String command, String name) implements Reward {

    @Override
    public void give(Player player) {
        var server = player.getServer();
        var run = command.replace("%player%", player.getName());
        if (!server.dispatchCommand(server.getConsoleSender(), run)) {
            throw new RewardFailedException("The command '" + run + "' is unknown or failed");
        }
    }

    /** {@code command: <command>} with a {@code name}, since the command can't tell players what it gives. */
    static final class Type implements RewardType {

        @Override
        public String key() {
            return "command";
        }

        @Override
        public Reward read(ConfigReader entry) {
            var command = entry.string("command");
            return new CommandReward(command.startsWith("/") ? command.substring(1) : command, entry.string("name"));
        }
    }
}
