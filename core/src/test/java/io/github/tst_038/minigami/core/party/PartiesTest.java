package io.github.tst_038.minigami.core.party;

import static io.github.tst_038.minigami.core.party.Parties.Result.FULL;
import static io.github.tst_038.minigami.core.party.Parties.Result.IN_PARTY;
import static io.github.tst_038.minigami.core.party.Parties.Result.NOT_IN_PARTY;
import static io.github.tst_038.minigami.core.party.Parties.Result.NOT_LEADER;
import static io.github.tst_038.minigami.core.party.Parties.Result.NO_INVITE;
import static io.github.tst_038.minigami.core.party.Parties.Result.OK;
import static io.github.tst_038.minigami.core.party.Parties.Result.SELF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartiesTest {

    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID cy = UUID.randomUUID();
    private final UUID dee = UUID.randomUUID();
    private final Parties parties = new Parties(() -> 3);

    private void join(UUID leader, UUID... others) {
        for (var other : others) {
            assertEquals(OK, parties.invite(leader, other));
            assertEquals(OK, parties.accept(other, leader));
        }
    }

    @Test
    void invitingMakesTheInviterLeaderOfAPartyOfOne() {
        assertEquals(OK, parties.invite(ann, bob));

        var party = parties.of(ann).orElseThrow();
        assertEquals(ann, party.leader());
        assertEquals(List.of(ann), party.members());
        assertEquals(Optional.empty(), parties.of(bob));
    }

    @Test
    void acceptingJoinsInOrder() {
        join(ann, bob, cy);

        assertEquals(List.of(ann, bob, cy), parties.of(cy).orElseThrow().members());
        assertEquals(parties.of(ann), parties.of(cy));
        assertEquals(1, parties.all().size());
    }

    @Test
    void aMemberCannotInviteAndNobodyInvitesThemselvesOrSomeoneInAParty() {
        join(ann, bob);

        assertEquals(NOT_LEADER, parties.invite(bob, cy));
        assertEquals(SELF, parties.invite(ann, ann));
        assertEquals(IN_PARTY, parties.invite(ann, bob));
        assertEquals(Optional.empty(), parties.of(cy));
    }

    @Test
    void acceptingNeedsAnOpenInvite() {
        parties.invite(ann, bob);

        assertEquals(NO_INVITE, parties.accept(cy, ann));
        assertEquals(NO_INVITE, parties.accept(bob, cy));
        assertEquals(OK, parties.accept(bob, ann));
        assertEquals(IN_PARTY, parties.accept(bob, ann));
    }

    @Test
    void anExpiredInviteCannotBeAccepted() {
        parties.invite(ann, bob);

        assertTrue(parties.expire(ann, bob));
        assertFalse(parties.expire(ann, bob));
        assertEquals(NO_INVITE, parties.accept(bob, ann));
    }

    @Test
    void anInviteIsOnePartyOnly() {
        parties.invite(ann, cy);
        join(bob, dee);

        assertEquals(IN_PARTY, parties.accept(dee, ann));
        assertEquals(OK, parties.accept(cy, ann));
        assertEquals(IN_PARTY, parties.accept(cy, bob));
    }

    @Test
    void thePartyHasAMaximumSize() {
        join(ann, bob, cy);

        assertEquals(FULL, parties.invite(ann, dee));
    }

    @Test
    void roomThatRanOutBeforeAcceptingStillCounts() {
        parties.invite(ann, bob);
        parties.invite(ann, cy);
        parties.invite(ann, dee);
        parties.accept(bob, ann);
        parties.accept(cy, ann);

        assertEquals(FULL, parties.accept(dee, ann));
    }

    @Test
    void aMemberLeavingKeepsTheRest() {
        join(ann, bob, cy);

        assertEquals(OK, parties.leave(bob));

        assertEquals(List.of(ann, cy), parties.of(ann).orElseThrow().members());
        assertEquals(Optional.empty(), parties.of(bob));
    }

    @Test
    void theLeaderLeavingHandsOverToTheLongestMember() {
        join(ann, bob, cy);

        parties.leave(ann);

        var party = parties.of(bob).orElseThrow();
        assertEquals(bob, party.leader());
        assertEquals(List.of(bob, cy), party.members());
    }

    @Test
    void aPartyLeftWithOneIsDisbanded() {
        join(ann, bob);

        parties.leave(bob);

        assertEquals(Optional.empty(), parties.of(ann));
        assertEquals(List.of(), parties.all());
    }

    @Test
    void theOnlyMemberLeavingDisbandsToo() {
        parties.invite(ann, bob);

        assertEquals(OK, parties.leave(ann));

        assertEquals(List.of(), parties.all());
        assertEquals(NO_INVITE, parties.accept(bob, ann));
        assertEquals(NOT_IN_PARTY, parties.leave(ann));
    }

    @Test
    void onlyTheLeaderKicksAndOnlyAMember() {
        join(ann, bob, cy);

        assertEquals(NOT_LEADER, parties.kick(bob, cy));
        assertEquals(NOT_IN_PARTY, parties.kick(ann, dee));
        assertEquals(NOT_IN_PARTY, parties.kick(dee, ann));
        assertEquals(SELF, parties.kick(ann, ann));
        assertEquals(OK, parties.kick(ann, cy));
        assertEquals(List.of(ann, bob), parties.of(ann).orElseThrow().members());
        assertEquals(Optional.empty(), parties.of(cy));
    }

    @Test
    void kickingTheSecondToLastMemberDisbands() {
        join(ann, bob);

        parties.kick(ann, bob);

        assertEquals(List.of(), parties.all());
    }

    @Test
    void onlyTheLeaderDisbands() {
        join(ann, bob, cy);

        assertEquals(NOT_LEADER, parties.disband(bob));
        assertEquals(NOT_IN_PARTY, parties.disband(dee));
        assertEquals(OK, parties.disband(ann));

        assertEquals(List.of(), parties.all());
        assertEquals(Optional.empty(), parties.of(bob));
        // Free to join another party now.
        assertEquals(OK, parties.invite(bob, ann));
    }

    @Test
    void disbandingFromCodeWorksOnAnyMember() {
        join(ann, bob);

        assertTrue(parties.disbandOf(bob));
        assertFalse(parties.disbandOf(bob));
        assertEquals(List.of(), parties.all());
    }

    @Test
    void aFormerLeadersInviteIsNoGoodAfterTheHandover() {
        var roomy = new Parties(() -> 4);
        roomy.invite(ann, bob);
        roomy.accept(bob, ann);
        roomy.invite(ann, cy);
        roomy.accept(cy, ann);
        roomy.invite(ann, dee);

        roomy.leave(ann);

        assertEquals(NO_INVITE, roomy.accept(dee, ann));
        assertEquals(NO_INVITE, roomy.accept(dee, bob));
    }

    @Test
    void restoreBringsBackAPartyAndSkipsMembersAlreadyPlaced() {
        join(ann, bob);
        var id = UUID.randomUUID();

        var restored = parties.restore(id, cy, List.of(cy, bob, dee));

        assertEquals(List.of(cy, dee), restored.orElseThrow().members());
        assertEquals(cy, parties.of(dee).orElseThrow().leader());
        assertEquals(ann, parties.of(bob).orElseThrow().leader());
        assertEquals(Optional.empty(), parties.restore(UUID.randomUUID(), bob, List.of(bob)));
    }
}
