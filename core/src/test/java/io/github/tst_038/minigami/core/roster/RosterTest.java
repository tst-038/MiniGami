package io.github.tst_038.minigami.core.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RosterTest {

    private final Roster roster = new Roster();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

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
}
