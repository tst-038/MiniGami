package io.github.tst_038.minigami.poleduel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.poleduel.Poles.Bounds;
import io.github.tst_038.minigami.poleduel.Poles.Column;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PolesTest {

    private static void assertValid(List<Column> poles, Bounds bounds, int count, int minDistance) {
        assertEquals(count, poles.size());
        for (int i = 0; i < poles.size(); i++) {
            var pole = poles.get(i);
            assertTrue(pole.x() >= bounds.minX() && pole.x() <= bounds.maxX() && pole.z() >= bounds.minZ() && pole.z() <= bounds.maxZ(), pole + " is outside");
            for (int j = i + 1; j < poles.size(); j++) {
                long dx = pole.x() - poles.get(j).x();
                long dz = pole.z() - poles.get(j).z();
                assertTrue(dx * dx + dz * dz >= (long) minDistance * minDistance, pole + " is too near " + poles.get(j));
            }
        }
    }

    @Test
    void theSameSeedGivesTheSamePoles() {
        var bounds = new Bounds(-20, 10, 30, 60);

        assertEquals(Poles.place(bounds, 8, 4, new Random(7)), Poles.place(bounds, 8, 4, new Random(7)));
    }

    @Test
    void anotherSeedGivesOtherPoles() {
        var bounds = new Bounds(0, 0, 50, 50);

        assertNotEquals(Poles.place(bounds, 8, 4, new Random(1)), Poles.place(bounds, 8, 4, new Random(2)));
    }

    @Test
    void polesStayInsideTheAreaAndTheMinimumDistanceApart() {
        var bounds = new Bounds(-20, 10, 30, 60);

        for (long seed = 0; seed < 50; seed++) {
            assertValid(Poles.place(bounds, 12, 4, new Random(seed)), bounds, 12, 4);
        }
    }

    @Test
    void aTightAreaFallsBackToAGridThatFitsExactly() {
        // Nine poles 4 apart fit a 9 by 9 area only on the grid 0, 4, 8; drawing at random does not find them.
        var bounds = new Bounds(0, 0, 8, 8);

        for (long seed = 0; seed < 50; seed++) {
            assertValid(Poles.place(bounds, 9, 4, new Random(seed)), bounds, 9, 4);
        }
    }

    @Test
    void anAreaWithoutRoomGivesNoPoles() {
        assertTrue(Poles.place(new Bounds(0, 0, 2, 2), 2, 4, new Random(1)).isEmpty());
        assertTrue(Poles.place(new Bounds(0, 0, 8, 8), 20, 4, new Random(1)).isEmpty());
        assertEquals(1, Poles.place(new Bounds(0, 0, 2, 2), 1, 4, new Random(1)).size());
    }

    @Test
    void polesOnlyStandWhereAllowed() {
        var bounds = new Bounds(0, 0, 20, 20);

        var poles = Poles.place(bounds, 4, 4, new Random(3), column -> column.x() >= 10);

        assertValid(poles, bounds, 4, 4);
        assertTrue(poles.stream().allMatch(pole -> pole.x() >= 10));
        assertTrue(Poles.place(bounds, 4, 4, new Random(3), column -> false).isEmpty());
    }

    @Test
    void needsAPoleAndADistance() {
        var bounds = new Bounds(0, 0, 5, 5);

        assertThrows(IllegalArgumentException.class, () -> Poles.place(bounds, 0, 4, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> Poles.place(bounds, 2, 0, new Random(1)));
    }
}
