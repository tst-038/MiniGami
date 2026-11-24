package io.github.tst_038.minigami.api.stat;

import io.github.tst_038.minigami.core.stat.Stat;
import java.util.Map;
import java.util.UUID;

/**
 * Lets other plugins keep stats in MiniGami outside matches, so they show in its stats commands, leaderboards (all time, weekly and
 * monthly) and placeholders like those of any game. Get it from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(MiniGamiStats.class)} (null while MiniGami isn't enabled).
 *
 * <p>Call it on the server thread.
 */
public interface MiniGamiStats {

    /**
     * Raises the stats of the players, as of today. Nothing waits for the storage: the work is done in the background, and a
     * failure is logged by MiniGami, not thrown here. Players who are online see it in their stats soon after. The leaderboards
     * take it up when they refresh next, every few minutes, not at once: a plugin may call this every second, and a board is not
     * read from storage for each call.
     *
     * <p>Stats are kept per game, so {@code game} should be an id that no MiniGami game uses; a stat of such a game is shown
     * as one of a game MiniGami has no file for. It is a lower case id, like those of the games: letters, digits and dashes.
     *
     * @param amounts what to add, per player and stat; a negative amount takes away. The map is copied, so it may be reused
     * @throws IllegalArgumentException if the game id is invalid, or a stat is {@code played} or {@code wins}, which MiniGami
     *         keeps itself from the matches it runs
     */
    void add(String game, Map<UUID, Map<Stat, Long>> amounts);
}
