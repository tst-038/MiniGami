package io.github.tst_038.minigami.paper.config;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The server-wide settings from {@code config.yml}.
 *
 * @param hud the HUD every game has unless it sets its own; read by the game loader, so only its shape is known here
 * @param partyMaxSize how many players a party holds at most
 */
public record Settings(Optional<Location> lobby, ConfigurationSection hud, int partyMaxSize) {

    static final int DEFAULT_PARTY_MAX_SIZE = 8;

    public static final Settings DEFAULTS = new Settings(Optional.empty(), SettingsLoader.bundledHud(), DEFAULT_PARTY_MAX_SIZE);

    public Settings {
        lobby = lobby.map(Location::clone);
    }

    @Override
    public Optional<Location> lobby() {
        return lobby.map(Location::clone);
    }
}
