package io.github.tst_038.minigami.api.reward;

import org.bukkit.entity.Player;

/**
 * Something a player can win, such as items or whatever a command gives. MiniGami reads these from
 * reward entries in its configuration, see {@link RewardReader}; other plugins may implement their own
 * and hand them to the places that take a {@code Reward}. New kinds may be added, so don't rely on
 * the known ones being the only ones.
 */
public interface Reward {

    /** What the winner is told they won, as MiniMessage text. */
    String name();

    /** Gives it to the player, who is online. Server thread only. */
    void give(Player player);
}
