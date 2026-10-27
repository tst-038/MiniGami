package io.github.tst_038.minigami.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VotesTest {

    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID cid = UUID.randomUUID();
    private final Votes votes = new Votes();

    @Test
    void aFirstVoteCountsInFull() {
        assertEquals(4, votes.vote(ann, bob, 4));
        assertEquals(2, votes.vote(ann, cid, 2));
    }

    @Test
    void votingAgainOnlyCountsTheDifference() {
        votes.vote(ann, bob, 2);

        assertEquals(3, votes.vote(ann, bob, 5));
        assertEquals(-4, votes.vote(ann, bob, 1));
    }

    @Test
    void votesOnDifferentBuildsAreSeparate() {
        votes.vote(ann, cid, 5);

        assertEquals(3, votes.vote(bob, cid, 3));
    }
}
