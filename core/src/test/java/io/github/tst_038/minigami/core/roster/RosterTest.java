package io.github.tst_038.minigami.core.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RosterTest {

    private final Roster roster = new Roster();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID dave = UUID.randomUUID();

    private List<UUID> ids(List<Participant> participants) {
        return participants.stream().map(Participant::id).toList();
    }

    @Test
    void addedParticipantIsAlive() {
        var participant = roster.add(alice);

        assertEquals(alice, participant.id());
        assertEquals(ParticipantState.ALIVE, participant.state());
        assertTrue(participant.isAlive());
    }

    @Test
    void addingTheSameParticipantTwiceFails() {
        roster.add(alice);

        assertThrows(IllegalArgumentException.class, () -> roster.add(alice));
    }

    @Test
    void findsParticipantById() {
        var participant = roster.add(alice);

        assertSame(participant, roster.find(alice).orElseThrow());
        assertTrue(roster.find(bob).isEmpty());
    }

    @Test
    void aliveListsSurvivorsInJoinOrder() {
        roster.add(alice);
        roster.add(bob);
        roster.add(carol);

        roster.eliminate(bob);

        assertEquals(List.of(alice, carol), ids(roster.alive()));
    }

    @Test
    void eliminationMarksParticipantEliminated() {
        var participant = roster.add(alice);

        assertTrue(roster.eliminate(alice));

        assertEquals(ParticipantState.ELIMINATED, participant.state());
        assertFalse(participant.isAlive());
    }

    @Test
    void recordsEliminationOrder() {
        roster.add(alice);
        roster.add(bob);
        roster.add(carol);

        roster.eliminate(carol);
        roster.eliminate(alice);

        assertEquals(List.of(carol, alice), ids(roster.eliminationOrder()));
    }

    @Test
    void eliminatingTwiceIsIgnored() {
        roster.add(alice);
        roster.eliminate(alice);

        assertFalse(roster.eliminate(alice));

        assertEquals(List.of(alice), ids(roster.eliminationOrder()));
    }

    @Test
    void eliminatingAnUnknownPlayerFails() {
        assertThrows(IllegalArgumentException.class, () -> roster.eliminate(alice));
    }

    @Test
    void returnedListsAreReadOnly() {
        roster.add(alice);
        roster.eliminate(alice);

        assertThrows(UnsupportedOperationException.class, () -> roster.alive().clear());
        assertThrows(UnsupportedOperationException.class, () -> roster.eliminationOrder().clear());
    }

    @Test
    void removedParticipantIsGoneAndCanJoinAgain() {
        roster.add(alice);
        roster.add(bob);

        assertTrue(roster.remove(alice));

        assertTrue(roster.find(alice).isEmpty());
        assertEquals(List.of(bob), ids(roster.alive()));
        assertEquals(1, roster.size());
        roster.add(alice);
    }

    @Test
    void removingAnEliminatedParticipantDropsThemFromTheOrder() {
        roster.add(alice);
        roster.add(bob);
        roster.eliminate(alice);

        roster.remove(alice);

        assertTrue(roster.eliminationOrder().isEmpty());
    }

    @Test
    void removingAnUnknownPlayerReturnsFalse() {
        assertFalse(roster.remove(alice));
    }

    @Test
    void withoutTeamsEveryoneIsATeamOfOne() {
        roster.add(alice);
        roster.add(bob);

        assertEquals(Optional.empty(), roster.find(alice).orElseThrow().team());
        assertEquals(List.of(new MatchTeam(Optional.empty(), List.of(roster.find(alice).orElseThrow())),
                new MatchTeam(Optional.empty(), List.of(roster.find(bob).orElseThrow()))), roster.teams());
    }

    @Test
    void teamsComeInTheOrderOfTheirFirstMemberThenEveryoneWithoutATeam() {
        roster.add(alice);
        roster.add(bob);
        roster.add(carol);
        roster.add(dave);

        roster.assignTeam(carol, "blue");
        roster.assignTeam(bob, "red");
        roster.assignTeam(alice, "blue");

        assertEquals(Optional.of("blue"), roster.find(alice).orElseThrow().team());
        var teams = roster.teams();
        assertEquals(List.of(Optional.of("blue"), Optional.of("red"), Optional.empty()), teams.stream().map(MatchTeam::id).toList());
        assertEquals(List.of(List.of(alice, carol), List.of(bob), List.of(dave)), teams.stream().map(MatchTeam::ids).toList());
    }

    @Test
    void assigningAgainMovesAParticipantAndAnEmptyTeamDisappears() {
        roster.add(alice);
        roster.assignTeam(alice, "red");

        roster.assignTeam(alice, "blue");

        assertEquals(List.of(Optional.of("blue")), roster.teams().stream().map(MatchTeam::id).toList());
    }

    @Test
    void eliminatedMembersStayInTheirTeamAndRemovedOnesLeaveIt() {
        roster.add(alice);
        roster.add(bob);
        roster.assignTeam(alice, "red");
        roster.assignTeam(bob, "red");

        roster.eliminate(alice);
        roster.remove(bob);

        assertEquals(List.of(List.of(alice)), roster.teams().stream().map(MatchTeam::ids).toList());
    }

    @Test
    void assigningAnUnknownPlayerFails() {
        assertThrows(IllegalArgumentException.class, () -> roster.assignTeam(alice, "red"));
    }

    @Test
    void aTeamIsOutOnceAllItsMembersAreAndWentOutWithItsLastMember() {
        roster.add(alice);
        roster.add(bob);
        roster.add(carol);
        roster.assignTeam(alice, "red");
        roster.assignTeam(bob, "red");

        roster.eliminate(bob);
        assertFalse(roster.teams().getFirst().isOut());

        roster.eliminate(carol);
        roster.eliminate(alice);

        assertEquals(List.of(List.of(carol), List.of(alice, bob)), roster.teamsOut().stream().map(MatchTeam::ids).toList());
        assertTrue(roster.teamsLeft().isEmpty());
    }
}
