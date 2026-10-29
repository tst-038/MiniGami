package io.github.tst_038.minigami.paper.game;

import java.util.List;

/**
 * How a game splits its players into teams at the start: into a fixed number of teams, or into teams
 * of a fixed size. Use {@link #ofCount} or {@link #ofSize}.
 *
 * @param count the number of teams, or 0 when set by size
 * @param size the players per team, or 0 when set by count
 */
public record TeamSetup(int count, int size) {

    /** Team ids in the order teams are handed out; each is also the team's colour. */
    public static final List<String> IDS = List.of("red", "blue", "green", "yellow", "aqua", "light_purple", "gold", "white");

    public static TeamSetup ofCount(int count) {
        return new TeamSetup(count, 0);
    }

    public static TeamSetup ofSize(int size) {
        return new TeamSetup(0, size);
    }

    /** The most players one team can hold when a match is full. */
    public int largestTeam(int maxPlayers) {
        return count > 0 ? Math.ceilDiv(maxPlayers, count) : size;
    }

    /** How many teams this many players are split into; at least one. */
    public int teamsFor(int players) {
        return count > 0 ? count : Math.max(1, Math.ceilDiv(players, size));
    }
}
