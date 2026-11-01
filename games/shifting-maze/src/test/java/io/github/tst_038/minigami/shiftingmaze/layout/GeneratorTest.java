package io.github.tst_038.minigami.shiftingmaze.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GeneratorTest {
    private static final int[][] SIZES = {{1, 1}, {1, 5}, {2, 3}, {7, 7}, {15, 9}};

    private static List<Boolean> state(Layout l) {
        return l.walls().stream().map(l::isOpen).toList();
    }

    @Test
    void connectedWithExpectedOpenCountForManySeeds() {
        for (int[] size : SIZES) {
            for (int loops : new int[] {0, 25}) {
                for (long seed = 0; seed < 200; seed++) {
                    Layout l = Generator.generate(size[0], size[1], loops, new Random(seed));
                    int cells = size[0] * size[1];
                    int walls = l.walls().size();
                    assertTrue(l.isConnected());
                    assertEquals(cells - 1 + Math.round(loops * (walls - cells + 1) / 100.0), l.openCount());
                }
            }
        }
    }

    @Test
    void sameSeedSameLayoutDifferentSeedsDiffer() {
        assertEquals(state(Generator.generate(7, 7, 20, new Random(1))), state(Generator.generate(7, 7, 20, new Random(1))));
        assertNotEquals(state(Generator.generate(7, 7, 20, new Random(1))), state(Generator.generate(7, 7, 20, new Random(2))));
    }

    @Test
    void zeroLoopsIsATree() {
        assertEquals(48, Generator.generate(7, 7, 0, new Random(3)).openCount());
    }

    @Test
    void wallNormalisesAndRejectsNonNeighbours() {
        assertEquals(new Wall(new Cell(0, 0), new Cell(1, 0)), new Wall(new Cell(1, 0), new Cell(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Wall(new Cell(0, 0), new Cell(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new Wall(new Cell(0, 0), new Cell(0, 0)));
    }

    @Test
    void pathAndDistancesFollowOpenWalls() {
        Layout l = Generator.generate(7, 7, 0, new Random(4));
        Cell far = new Cell(6, 6);
        List<Wall> path = l.path(new Cell(0, 0), far);
        assertEquals(l.distances(new Cell(0, 0))[6][6], path.size());
        assertTrue(path.stream().allMatch(l::isOpen));
        assertTrue(l.path(far, far).isEmpty());
    }
}
