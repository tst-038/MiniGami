package io.github.tst_038.minigami.api.board;

/** A claim on a player's scoreboard, from {@link MiniGamiBoards#claim}. */
public interface BoardClaim {

    /**
     * Gives the claim up: the player sees the next claim's board, or with none left the board they had before the first claim.
     * Releasing it again, or after the player quit, does nothing.
     */
    void release();
}
