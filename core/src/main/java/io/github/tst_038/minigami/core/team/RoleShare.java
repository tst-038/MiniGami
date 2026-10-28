package io.github.tst_038.minigami.core.team;

/**
 * How many players a role gets: a fixed number, one per so many players, or whoever is left. Use
 * {@link #of}, {@link #onePer} or {@link #rest}.
 *
 * @param players the fixed number, or 0
 * @param per one player per this many, or 0
 */
public record RoleShare(int players, int per) {

    public static RoleShare of(int players) {
        return new RoleShare(players, 0);
    }

    public static RoleShare onePer(int per) {
        return new RoleShare(0, per);
    }

    public static RoleShare rest() {
        return new RoleShare(0, 0);
    }

    public boolean isRest() {
        return players == 0 && per == 0;
    }

    /** How many of this many players the role asks for; at least one, and none for the rest role. */
    public int wanted(int total) {
        return players > 0 ? players : per > 0 ? Math.max(1, total / per) : 0;
    }
}
