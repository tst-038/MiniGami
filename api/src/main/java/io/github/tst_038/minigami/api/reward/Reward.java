package io.github.tst_038.minigami.api.reward;

import org.bukkit.entity.Player;

/**
 * What a player is given for winning or taking part. Read one from configuration with
 * {@link RewardReader#reward} and {@link #give} it to the player. New kinds may be added, so don't
 * rely on the implementations.
 */
public interface Reward {

    /** What the winner is told they won, as MiniMessage text. */
    String name();

    /**
     * Gives it to the player, who is online. Server thread only.
     *
     * @throws RewardFailedException when it could not be given
     */
    void give(Player player);
}
