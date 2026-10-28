package io.github.tst_038.minigami.api.feature;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * A participant who quit came back into their match. They are prepared as at the start and stand at
 * a spawn of their team, or spectate if they are out; features of the current phase set them up
 * again as they do on a respawn.
 */
public final class MatchRejoinEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public MatchRejoinEvent(Player player) {
        super(player);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
