package io.github.tst_038.minigami.core.life;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
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
    void livesAreAskedAtEachDeath() {
        livesOf.put(alice, 3);
        lives.die(alice);

        livesOf.put(alice, 2);

        assertTrue(lives.die(alice));
    }
}
