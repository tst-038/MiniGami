package io.github.tst_038.minigami.paper.game;

/** Where a player who comes back into a running match is put down. */
public enum Respawn {
    /** On one of the arena's spawns, as set up (the default). */
    SPAWNS,
    /** On the highest floor that is still standing in the region the game plays in; for games that break their floor, such as spleef. */
    HIGHEST_FLOOR
}
