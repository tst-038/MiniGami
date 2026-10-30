package io.github.tst_038.minigami.api.presence;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * MiniGami gave a player their own state back and no longer holds them: {@link MiniGamiPlayers#isInMatch}
 * is already false, and it is safe to give them items here. Fired exactly once for each
 * {@link MiniGamiPlayerJoinEvent}, whether the match ended, they left or were kicked, they quit, or
 * the plugin is disabled. Synchronous, on the server thread. Not cancellable.
 *
 * <p>The player is online: their state is only given back to an online player, and one whose
 * restore had to wait for their next login fires this at that login. When they quit, it fires
 * during their quit, while they are still online; what they are given is saved with them.
 * {@link #getPlayer()} is empty only if they went offline anyway, so use {@link #getPlayerId()}
 * to find them.
 */
public final class MiniGamiPlayerLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final Optional<Player> player;

    public MiniGamiPlayerLeaveEvent(UUID playerId, Optional<Player> player) {
        this.playerId = playerId;
        this.player = player;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /** The player if they are online. */
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
