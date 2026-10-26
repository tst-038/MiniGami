package io.github.tst_038.minigami.paper.config;

import java.util.Optional;
import org.bukkit.Location;

/** The server-wide settings from {@code config.yml}. */
public record Settings(Optional<Location> lobby) {

    public static final Settings DEFAULTS = new Settings(Optional.empty());

    public Settings {
        lobby = lobby.map(Location::clone);
    }

    @Override
    public Optional<Location> lobby() {
        return lobby.map(Location::clone);
    }
}
