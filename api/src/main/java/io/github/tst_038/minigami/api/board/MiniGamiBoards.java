package io.github.tst_038.minigami.api.board;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The one owner of what scoreboard each player sees, so plugins that show a sidebar do not overwrite each other or give
 * back a board that is out of date. Instead of {@code player.setScoreboard}, claim the player's scoreboard and release the
 * claim when your board is no longer wanted. Get it from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(MiniGamiBoards.class)} (null while MiniGami isn't enabled).
 *
 * <p>A player sees the board of the claim with the highest {@link BoardPriority}, and of equal ones the latest. Once a claim
 * is released the next one shows; when none is left the player gets the board they had before the first claim. A player's
 * claims are dropped when they quit, and a plugin's when it is disabled.
 *
 * <p>Call it on the server thread.
 */
public interface MiniGamiBoards {

    /**
     * Claims the player's scoreboard for the board, which shows at once unless a claim of higher priority holds it.
     *
     * @param owner the plugin the claim belongs to; its claims are released when it is disabled
     */
    BoardClaim claim(Plugin owner, Player player, Scoreboard board, BoardPriority priority);
}
