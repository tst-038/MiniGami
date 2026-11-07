package io.github.tst_038.minigami.core.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.core.spawn.Spread.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpreadTest {

    private static final List<Point> CANDIDATES = List.of(new Point(0, 0, 0), new Point(10, 0, 0), new Point(20, 0, 0));

    @Test
    void takesTheCandidateFarthestFromTheNearestPlayer() {
        assertEquals(2, Spread.farthest(CANDIDATES, List.of(new Point(1, 0, 0), new Point(12, 0, 0))));
    }

    @Test
    void aCrowdedCandidateLosesToAnEmptyOne() {
        assertEquals(0, Spread.farthest(CANDIDATES, List.of(new Point(10, 0, 0), new Point(20, 0, 0))));
    }

    @Test
    void withNobodyElseTheFirstIsTaken() {
        assertEquals(0, Spread.farthest(CANDIDATES, List.of()));
    }
}
