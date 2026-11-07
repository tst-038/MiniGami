package io.github.tst_038.minigami.paper.game;

/** How the members of a team are placed on the arena's spawns that are not meant for one team. */
public enum TeamSpawns {
    /** A team shares one spawn and comes back to it, and teams are kept apart; as a wall game needs. */
    TOGETHER,
    /** Every player takes the next spawn whatever their team, and comes back to the spawn farthest from the others. */
    SPREAD
}
