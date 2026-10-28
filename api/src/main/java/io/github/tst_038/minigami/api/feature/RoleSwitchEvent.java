package io.github.tst_038.minigami.api.feature;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * A participant went out without dying and joined another role on the spot, such as a hider tagged
 * into a seeker. Their items and effects are cleared; features of the current phase set them up for
 * the new role as they do on a respawn.
 */
public final class RoleSwitchEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public RoleSwitchEvent(Player player) {
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
