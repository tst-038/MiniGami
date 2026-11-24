package io.github.tst_038.minigami.koth.stats;

import io.github.tst_038.minigami.core.stat.Stat;

/**
 * What KOTH counts per player, kept in MiniGami under the game {@link #GAME} so its boards, profiles and
 * placeholders show them. Kills and deaths have the ids MiniGami's own games use, so they share their names.
 */
public final class KothStats {

    /** The game the stats are kept under. */
    public static final String GAME = "koth";

    /** Every point earned on the hill, whether spent since or not. */
    public static final Stat POINTS = Stat.POINTS;
    public static final Stat KILLS = new Stat("kills");
    public static final Stat DEATHS = new Stat("deaths");
    /** The times the hill changed hands to the player. */
    public static final Stat CAPTURES = new Stat("captures");

    private KothStats() {
    }
}
