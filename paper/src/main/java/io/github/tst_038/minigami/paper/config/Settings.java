package io.github.tst_038.minigami.paper.config;

import io.github.tst_038.minigami.paper.game.Start;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The server-wide settings from {@code config.yml}.
 *
 * @param blocksPerTick how many blocks a tick may place when a game builds without FastAsyncWorldEdit
 * @param hud the HUD every game has unless it sets its own; read by the game loader, so only its shape is known here
 * @param start how matches start, for every game that doesn't set its own
 * @param partyInviteSeconds how long a party invite can be accepted
 */
public record Settings(Optional<Location> lobby, int blocksPerTick, ConfigurationSection hud, Start start, int partyInviteSeconds,
        LeaderboardSettings leaderboards) {

    public static final Settings DEFAULTS = new Settings(Optional.empty(), SettingsLoader.DEFAULT_BLOCKS_PER_TICK, SettingsLoader.bundledHud(),
            Start.DEFAULT, SettingsLoader.DEFAULT_PARTY_INVITE_SECONDS, LeaderboardSettings.DEFAULT);

    /**
     * @param size how many players a board shows
     * @param refreshSeconds how often boards are read again from storage; they lag this much behind, but are never read per view
     */
    public record LeaderboardSettings(int size, int refreshSeconds) {

        public static final LeaderboardSettings DEFAULT = new LeaderboardSettings(10, 5 * 60);

        public long refreshTicks() {
            return refreshSeconds * 20L;
        }
    }

    public Settings {
        lobby = lobby.map(Location::clone);
    }

    @Override
    public Optional<Location> lobby() {
        return lobby.map(Location::clone);
    }

    public long partyInviteTicks() {
        return partyInviteSeconds * 20L;
    }
}
