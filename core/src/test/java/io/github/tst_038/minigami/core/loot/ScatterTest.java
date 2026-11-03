package io.github.tst_038.minigami.core.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ScatterTest {

    private static final List<Integer> FREE = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

    @Test
    void picksDistinctFreeSlots() {
        var slots = Scatter.slots(FREE, 6, new Random(1));
        assertEquals(6, slots.size());
        assertEquals(6, new HashSet<>(slots).size());
        assertTrue(FREE.containsAll(slots));
    }

    @Test
    void givesOnlyTheFreeSlotsThereAre() {
        assertEquals(3, Scatter.slots(List.of(4, 5, 6), 10, new Random(1)).size());
        assertEquals(List.of(), Scatter.slots(List.of(), 3, new Random(1)));
    }

    @Test
    void doesNotStartAtTheFirstSlot() {
        var firsts = new HashSet<Integer>();
        for (int seed = 0; seed < 50; seed++) {
            firsts.add(Scatter.slots(FREE, 1, new Random(seed)).getFirst());
        }
        assertTrue(firsts.size() > 3, "the slot should vary: " + firsts);
    }
}
