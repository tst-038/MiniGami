package io.github.tst_038.minigami.paper.match;

import static io.github.tst_038.minigami.paper.match.PartyJoin.OK;
import static io.github.tst_038.minigami.paper.match.PartyJoin.TOO_BIG_FOR_GAME;
import static io.github.tst_038.minigami.paper.match.PartyJoin.TOO_BIG_FOR_TEAM;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.paper.game.TeamSetup;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PartyJoinTest {

    @Test
    void aPartyWithoutTeamsOnlyNeedsRoomInTheGame() {
        assertEquals(OK, PartyJoin.check(8, 8, OptionalInt.empty()));
        assertEquals(TOO_BIG_FOR_GAME, PartyJoin.check(9, 8, OptionalInt.empty()));
    }

    @Test
    void aPartyMustFitInOneTeam() {
        assertEquals(OK, PartyJoin.check(2, 8, OptionalInt.of(2)));
        assertEquals(TOO_BIG_FOR_TEAM, PartyJoin.check(3, 8, OptionalInt.of(2)));
    }

    @Test
    void theGameLimitComesBeforeTheTeamLimit() {
        assertEquals(TOO_BIG_FOR_GAME, PartyJoin.check(5, 4, OptionalInt.of(2)));
    }

    @Test
    void teamSizeFollowsTheTeamSetup() {
        assertEquals(2, TeamSetup.ofSize(2).largestTeam(8));
        assertEquals(4, TeamSetup.ofCount(2).largestTeam(8));
        assertEquals(4, TeamSetup.ofCount(2).largestTeam(7));
    }
}
