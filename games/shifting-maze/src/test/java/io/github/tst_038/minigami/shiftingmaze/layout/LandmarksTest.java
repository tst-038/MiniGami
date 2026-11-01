package io.github.tst_038.minigami.shiftingmaze.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LandmarksTest {
    private static Layout grid(int c, int r) {
        return new Layout(c, r);
    }

    @Test
    void cornersInOrderAndRepeatingForManyTeams() {
        Landmarks l = Landmarks.place(grid(7, 5), 5, 1, new Random(1));
        assertEquals(List.of(new Cell(0, 0), new Cell(6, 4), new Cell(6, 0), new Cell(0, 4), new Cell(0, 0)), l.starts());
        assertEquals(List.of(new Cell(0, 0)), Landmarks.place(grid(7, 5), 1, 1, new Random(1)).starts());
        assertEquals(new Cell(3, 2), l.exit());
    }

    @Test
    void lairsAreEdgeMidpointsApartFromStartsAndExit() {
        for (int[] s : new int[][] {{3, 3}, {7, 5}, {8, 8}}) {
            Landmarks l = Landmarks.place(grid(s[0], s[1]), 4, 1, new Random(1));
            assertEquals(4, l.lairs().size());
            assertEquals(new Cell(s[0] / 2, 0), l.lairs().get(0));
            assertEquals(new Cell(s[0] - 1, s[1] / 2), l.lairs().get(3));
            for (Cell lair : l.lairs()) {
                assertFalse(l.starts().contains(lair));
                assertFalse(l.exit().equals(lair));
            }
        }
    }

    @Test
    void keysAreDistinctAndOffLandmarks() {
        for (long seed = 0; seed < 50; seed++) {
            Landmarks l = Landmarks.place(grid(7, 7), 3, 4, new Random(seed));
            Set<Cell> seen = new HashSet<>();
            for (List<Cell> team : l.keys()) {
                assertEquals(4, team.size());
                for (Cell k : team) {
                    assertTrue(seen.add(k));
                    assertFalse(k.equals(l.exit()) || l.lairs().contains(k) || l.starts().contains(k));
                }
            }
        }
    }

    @Test
    void keysAreCappedOnSmallGrids() {
        // 3x3: 9 cells - 4 corners - centre - 4 edge midpoints leaves nothing
        assertEquals(List.of(List.of(), List.of()), Landmarks.place(grid(3, 3), 2, 3, new Random(1)).keys());
        // 5x5: 25 - 4 - 1 - 4 = 16 free, so 3 teams get 5 each at most
        List<List<Cell>> keys = Landmarks.place(grid(5, 5), 3, 9, new Random(1)).keys();
        assertEquals(List.of(5, 5, 5), keys.stream().map(List::size).toList());
    }

    @Test
    void zeroTeamsGivesNothing() {
        Landmarks l = Landmarks.place(grid(5, 5), 0, 3, new Random(1));
        assertTrue(l.starts().isEmpty());
        assertTrue(l.keys().isEmpty());
    }

    @Test
    void sameSeedSamePlacement() {
        assertEquals(Landmarks.place(grid(9, 9), 3, 3, new Random(5)), Landmarks.place(grid(9, 9), 3, 3, new Random(5)));
    }
}
