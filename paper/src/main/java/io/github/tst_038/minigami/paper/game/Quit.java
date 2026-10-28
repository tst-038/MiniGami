package io.github.tst_038.minigami.paper.game;

/**
 * What quitting a running match does to a player still in it, and how long they have to come back.
 *
 * @param rejoinSeconds 0: no coming back, so quitting puts them out whatever {@code then} says
 */
public record Quit(Then then, int rejoinSeconds) {

    /** As before quitting could be configured: out at once. */
    public static final Quit OUT = new Quit(Then.OUT, 0);

    public enum Then {
        /** Nothing lost; they keep their place until they come back or time runs out. */
        STAY,
        /** A death where they stood, with everything a death does in the mode. */
        DIE,
        /** Out of the match; coming back is as a spectator. */
        OUT
    }
}
