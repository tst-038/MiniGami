package io.github.tst_038.minigami.core.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivesTest {

    private final UUID alice = UUID.randomUUID();
    private final Map<UUID, Integer> livesOf = new HashMap<>();
    private final Lives lives = new Lives(id -> livesOf.getOrDefault(id, 0));

    @Test
    void theLastLifeIsTheOneThatCounts() {
        livesOf.put(alice, 2);

        assertFalse(lives.die(alice));
        assertTrue(lives.die(alice));
    }

    @Test
    void livesLeftCountDownAndAreEmptyWhenUnlimited() {
        assertEquals(OptionalInt.empty(), lives.left(alice));
        livesOf.put(alice, 3);
        lives.die(alice);

        assertEquals(OptionalInt.of(2), lives.left(alice));
    }

    @Test
    void unlimitedLivesNeverRunOut() {
        for (int i = 0; i < 100; i++) {
            assertFalse(lives.die(alice));
        }
    }

    @Test
    void resettingGivesFullLivesBack() {
        livesOf.put(alice, 2);
        lives.die(alice);

        lives.reset(alice);

        assertFalse(lives.die(alice));
    }

    @Test
    void endingRespawnsMakesTheNextDeathTheLastEvenWithLivesLeft() {
        livesOf.put(alice, 3);
        lives.endRespawns(alice);

        assertEquals(OptionalInt.of(0), lives.left(alice));
        assertTrue(lives.die(alice));
    }

    @Test
    void endingRespawnsAlsoHoldsForUnlimitedLives() {
        lives.endRespawns(alice);

        assertEquals(OptionalInt.of(0), lives.left(alice));
        assertTrue(lives.die(alice));
    }

    @Test
    void endingRespawnsTwiceOrForAnUnknownIdIsHarmless() {
        var bob = UUID.randomUUID();
        livesOf.put(alice, 2);
        lives.endRespawns(alice);
        lives.endRespawns(alice);
        lives.endRespawns(bob);

        assertTrue(lives.die(alice));
        assertTrue(lives.die(alice));
        assertTrue(lives.die(bob));
    }

    @Test
    void endingRespawnsIsOnlyForThatParticipant() {
        var bob = UUID.randomUUID();
        livesOf.put(alice, 2);
        livesOf.put(bob, 2);
        lives.endRespawns(alice);

        assertFalse(lives.die(bob));
    }

    @Test
    void ranOutLivesStayRanOutAfterEndingRespawns() {
        livesOf.put(alice, 1);
        lives.die(alice);
        lives.endRespawns(alice);

        assertEquals(OptionalInt.of(0), lives.left(alice));
    }

    @Test
    void resettingTakesBackTheEndOfRespawns() {
        livesOf.put(alice, 2);
        lives.endRespawns(alice);

        lives.reset(alice);

        assertEquals(OptionalInt.of(2), lives.left(alice));
        assertFalse(lives.die(alice));
    }

    @Test
    void livesAreAskedAtEachDeath() {
        livesOf.put(alice, 3);
        lives.die(alice);

        livesOf.put(alice, 2);

        assertTrue(lives.die(alice));
    }
}
