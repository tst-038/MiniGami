package io.github.tst_038.minigami.koth.hill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HillTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private final Hill hill = new Hill(3, new Scoring(2, 2));

    private HillTick ticks(int seconds, UUID... onPoint) {
        var last = HillTick.NOTHING;
        for (int i = 0; i < seconds; i++) {
            last = hill.tick(List.of(onPoint));
        }
        return last;
    }

    @Test
    void aPlayerAloneForTheCaptureTimeTakesTheHillAndEarnsNothingThatSecond() {
        assertEquals(HillTick.NOTHING, ticks(2, A));
        assertEquals(Optional.of(A), hill.taker());
        assertEquals(2, hill.progress());

        var tick = ticks(1, A);

        assertEquals(new HillTick(Optional.of(A), Optional.empty(), 0), tick);
        assertEquals(Optional.of(A), hill.holder());
    }

    @Test
    void theHolderEarnsEveryScoringInterval() {
        ticks(3, A);

        assertEquals(HillTick.NOTHING, ticks(1));
        assertEquals(new HillTick(Optional.empty(), Optional.of(A), 2), ticks(1));
        assertEquals(HillTick.NOTHING, ticks(1, A));
        assertEquals(new HillTick(Optional.empty(), Optional.of(A), 2), ticks(1, A));
    }

    @Test
    void theHolderKeepsTheHillWhileSomeoneContestsIt() {
        ticks(3, A);

        ticks(5, A, B);

        assertEquals(Optional.of(A), hill.holder());
    }

    @Test
    void anotherPlayerTakingTheHillRestartsTheCount() {
        ticks(3, A);
        ticks(1);

        var tick = ticks(3, B);

        assertEquals(new HillTick(Optional.of(B), Optional.empty(), 0), tick);
        assertEquals(HillTick.NOTHING, ticks(1));
    }

    @Test
    void aFreeHillEarnsNothing() {
        assertEquals(HillTick.NOTHING, ticks(10));
    }

    @Test
    void droppingFreesTheHillAndForgetsTheTaker() {
        ticks(3, A);
        ticks(1, B);

        hill.drop();

        assertEquals(Optional.empty(), hill.holder());
        assertEquals(Optional.empty(), hill.taker());
        assertEquals(HillTick.NOTHING, ticks(4));
    }

    @Test
    void scoringIsChecked() {
        assertThrows(IllegalArgumentException.class, () -> new Scoring(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Scoring(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Scoring(1, 3601));
    }
}
