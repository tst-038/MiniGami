package io.github.tst_038.minigami.paper.reward;

import org.bukkit.entity.Player;

/** Something a player can win, such as items or whatever a command gives. */
public interface Reward {

    /** What the winner is told they won, as MiniMessage text. */
    String name();

    /** Gives it to the player, who is online. Server thread only. */
    void give(Player player);
}
