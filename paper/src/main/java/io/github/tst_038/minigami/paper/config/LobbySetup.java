package io.github.tst_038.minigami.paper.config;

import io.github.tst_038.minigami.api.message.Messages;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Sets the lobby in {@code config.yml} to where the admin stands, then loads the config again. */
public final class LobbySetup {

    private final Plugin plugin;
    private final Messages messages;
    private final Consumer<Audience> reload;

    /** @param reload loads the config again, off the server thread, and tells this audience how that went */
    public LobbySetup(Plugin plugin, Messages messages, Consumer<Audience> reload) {
        this.plugin = plugin;
        this.messages = messages;
        this.reload = reload;
    }

    /** Call from the server thread; the file is written off it. */
    public void setLobby(Player player) {
        var lobby = ConfigLocations.writeWithWorld(player.getLocation());
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            var path = plugin.getDataPath().resolve(SettingsLoader.FILE);
            // Synchronized so two quick changes in a row don't overwrite each other.
            synchronized (this) {
                try {
                    ConfigFiles.update(path, config -> config.set("lobby", lobby));
                } catch (IOException | InvalidConfigurationException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not write " + path, e);
                    messages.send(player, "lobby.save-failed");
                    return;
                }
            }
            messages.send(player, "lobby.set");
            reload.accept(player);
        });
    }
}
