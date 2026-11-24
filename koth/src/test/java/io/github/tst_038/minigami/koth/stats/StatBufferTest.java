package io.github.tst_038.minigami.koth.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatBufferTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void sameStatsAreMergedPerPlayer() {
        var buffer = new StatBuffer();
        buffer.add(ALICE, KothStats.POINTS, 2);
        buffer.add(ALICE, KothStats.POINTS, 3);
        buffer.add(ALICE, KothStats.KILLS, 1);
        buffer.add(BOB, KothStats.POINTS, 4);

        assertEquals(Map.of(ALICE, Map.of(KothStats.POINTS, 5L, KothStats.KILLS, 1L), BOB, Map.of(KothStats.POINTS, 4L)), buffer.drain());
    }

    @Test
    void drainingClearsTheBuffer() {
        var buffer = new StatBuffer();
        assertTrue(buffer.isEmpty());
        buffer.add(ALICE, KothStats.CAPTURES, 1);
        assertFalse(buffer.isEmpty());

        buffer.drain();

        assertTrue(buffer.isEmpty());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test
    void anAmountOfZeroOrLessIsRejected() {
        var buffer = new StatBuffer();

        assertThrows(IllegalArgumentException.class, () -> buffer.add(ALICE, KothStats.KILLS, 0));
        assertThrows(IllegalArgumentException.class, () -> buffer.add(ALICE, KothStats.KILLS, -2));
        assertTrue(buffer.isEmpty());
    }

    @Test
    void aDrainedCopyIsNotChangedByLaterCounts() {
        var buffer = new StatBuffer();
        buffer.add(ALICE, KothStats.POINTS, 1);
        var drained = buffer.drain();

        buffer.add(ALICE, KothStats.POINTS, 1);

        assertEquals(Map.of(ALICE, Map.of(KothStats.POINTS, 1L)), drained);
    }
}
