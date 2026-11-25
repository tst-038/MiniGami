package io.github.tst_038.minigami.api.board;

/** How much a claim on a player's scoreboard counts, highest first: a claim hides every one of lower priority. */
public enum BoardPriority {

    /** MiniGami's matches. */
    MATCH,
    /** MiniGami's game nights, between their games. */
    NIGHT,
    /** Other plugins' own sidebars. */
    PLUGIN
}
