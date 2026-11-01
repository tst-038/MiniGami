package io.github.tst_038.minigami.shiftingmaze.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ShifterTest {
    private static final int[][] SIZES = {{2, 3}, {7, 7}, {15, 9}};
    /** Refuses about 30% of the walls, always the same ones. */
    private static final Predicate<Wall> CLOSABLE = w -> Math.floorMod(w.hashCode(), 10) >= 3;

    @Test
    void roundsKeepConnectedCountAndRespectPredicate() {
        for (int[] size : SIZES) {
            for (long seed = 0; seed < 100; seed++) {
                Random random = new Random(seed);
                Layout l = Generator.generate(size[0], size[1], 20, random);
                int open = l.openCount();
                for (int round = 0; round < 500; round++) {
                    List<Shift> shifts = Shifter.planRound(l, 3, random, CLOSABLE);
                    Layout planned = l.copy();
                    for (Shift s : shifts) {
                        assertTrue(CLOSABLE.test(s.closed()));
                        assertFalse(planned.isOpen(s.opened()));
                        assertTrue(planned.isOpen(s.closed()));
                        assertTrue(Shifter.apply(planned, s, CLOSABLE));
                    }
                    // applying only every second shift (1st and 3rd) must still leave a connected maze
                    for (int i = 0; i < shifts.size(); i += 2) {
                        Shifter.apply(l, shifts.get(i), CLOSABLE);
                        assertTrue(l.isConnected());
                        assertEquals(open, l.openCount());
                    }
                }
            }
        }
    }

    @Test
    void refuseAllAndSingleCellGiveNothing() {
        Random random = new Random(1);
        assertEquals(Optional.empty(), Shifter.plan(Generator.generate(7, 7, 0, random), random, w -> false));
        assertEquals(Optional.empty(), Shifter.plan(new Layout(1, 1), random, w -> true));
        assertTrue(Shifter.planRound(Generator.generate(7, 7, 0, random), 3, random, w -> false).isEmpty());
    }

    @Test
    void staleShiftIsDroppedAndLayoutUntouched() {
        Random random = new Random(3);
        Layout l = Generator.generate(5, 5, 0, random);
        Shift s = Shifter.plan(l, random, w -> true).orElseThrow();
        assertTrue(Shifter.apply(l, s, w -> true));
        assertFalse(Shifter.apply(l, s, w -> true));
        assertTrue(l.isConnected());
    }

    @Test
    void sameSeedSameShifts() {
        List<Shift> a = Shifter.planRound(Generator.generate(7, 7, 20, new Random(5)), 5, new Random(9), w -> true);
        List<Shift> b = Shifter.planRound(Generator.generate(7, 7, 20, new Random(5)), 5, new Random(9), w -> true);
        assertEquals(a, b);
        assertFalse(a.isEmpty());
    }
}
