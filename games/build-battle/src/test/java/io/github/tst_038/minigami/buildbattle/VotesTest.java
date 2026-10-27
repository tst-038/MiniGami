package io.github.tst_038.minigami.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.core.result.Placement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VotesTest {

    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID cid = UUID.randomUUID();
    private final Votes votes = new Votes();

    @Test
    void votingAgainReplacesTheVote() {
        votes.vote(ann, bob, 2);
        votes.vote(ann, bob, 5);
        votes.vote(ann, cid, 1);

        assertEquals(6, votes.total(ann));
    }

    @Test
    void highestTotalWinsAndTiesSharePlaces() {
        votes.vote(ann, bob, 3);
        votes.vote(bob, ann, 3);
        votes.vote(cid, ann, 5);

        assertEquals(List.of(new Placement(1, List.of(cid)), new Placement(2, List.of(ann, bob))),
                votes.result(List.of(ann, bob, cid)).placements());
    }

    @Test
    void aTieForFirstSkipsSecond() {
        votes.vote(ann, cid, 4);
        votes.vote(bob, cid, 4);
        votes.vote(cid, ann, 1);

        var places = votes.result(List.of(ann, bob, cid)).placements().stream().map(Placement::place).toList();

        assertEquals(List.of(1, 3), places);
    }

    @Test
    void aBuildWithoutVotesScoresNothing() {
        assertEquals(0, votes.total(ann));
        assertEquals(List.of(new Placement(1, List.of(ann))), votes.result(List.of(ann)).placements());
    }
}
