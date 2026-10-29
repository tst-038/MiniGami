package io.github.tst_038.minigami.paper.match;

import java.util.OptionalInt;

/** Whether a party can join a game together; a party has no size of its own, so the game decides. */
public enum PartyJoin {
    OK, TOO_BIG_FOR_GAME, TOO_BIG_FOR_TEAM;

    /**
     * @param size how many of the party join
     * @param maxPlayers the most players the game holds
     * @param teamSize the most players in one team, empty when the game has no teams
     */
    public static PartyJoin check(int size, int maxPlayers, OptionalInt teamSize) {
        if (size > maxPlayers) {
            return TOO_BIG_FOR_GAME;
        }
        return teamSize.isPresent() && size > teamSize.getAsInt() ? TOO_BIG_FOR_TEAM : OK;
    }
}
