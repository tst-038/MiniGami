package io.github.tst_038.minigami.paper.mode;

/** Where players are between joining a mode and the start of its match. */
public enum Waiting {
    /** They stay where they are and keep their own state until the start. */
    IN_PLACE,
    /** They wait in the server's lobby. */
    LOBBY,
    /** They wait in the arena itself. */
    ARENA
}
