package io.github.tst_038.minigami.api.presence;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * MiniGami took a player over: from now on {@link MiniGamiPlayers#isInMatch} is true for them, and
 * their own state (inventory and so on) is saved until the matching {@link MiniGamiPlayerLeaveEvent}.
 * Fired once per take-over, so not again when a player moves between the games of a game night, nor
 * when they quit and rejoin the same match in time. Synchronous, on the server thread. Not cancellable.
 */
public final class MiniGamiPlayerJoinEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final Optional<Player> player;

    public MiniGamiPlayerJoinEvent(UUID playerId, Optional<Player> player) {
        this.playerId = playerId;
        this.player = player;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /** The player, who is online when they are taken over. */
    public Optional<Player> getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
