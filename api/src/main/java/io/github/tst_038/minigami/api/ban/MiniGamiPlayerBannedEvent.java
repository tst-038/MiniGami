package io.github.tst_038.minigami.api.ban;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A player was banned and the ban is saved; {@link MiniGamiBans#ban(java.util.UUID, String)} already finds it. A plugin
 * takes the player out of what the ban {@link Ban#covers covers}, if they are in it. MiniGami does so for its matches and
 * game nights. Synchronous, on the server thread. Not cancellable.
 */
public final class MiniGamiPlayerBannedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Optional<Player> player;
    private final Ban ban;

    /** @param player the banned player, if online */
    public MiniGamiPlayerBannedEvent(Optional<Player> player, Ban ban) {
        this.player = player;
        this.ban = ban;
    }

    public Ban getBan() {
        return ban;
    }

    /** The banned player, if online. */
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
