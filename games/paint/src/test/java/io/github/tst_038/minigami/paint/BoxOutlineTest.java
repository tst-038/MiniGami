package io.github.tst_038.minigami.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class BoxOutlineTest {

    @Test
    void cornersAndEdgePointsAreDistinctAndOnTheEdgesOfTheBox() {
        var points = BoxOutline.points(1, 2, 3, 2, 3, 4, 2);

        assertEquals(8 + 12, points.size());
        assertEquals(points.size(), points.stream().map(Arrays::toString).collect(java.util.stream.Collectors.toCollection(HashSet::new)).size());
        // On an edge exactly two coordinates sit on a face of the box.
        points.forEach(p -> assertTrue(onFace(p[0], 1, 2) + onFace(p[1], 2, 3) + onFace(p[2], 3, 4) >= 2));
        assertTrue(points.stream().anyMatch(p -> Arrays.equals(p, new double[] {1.5, 2, 3})));
    }

    @Test
    void oneSegmentIsJustTheCorners() {
        assertEquals(8, BoxOutline.points(0, 0, 0, 1, 1, 1, 1).size());
    }

    private static int onFace(double value, double min, double max) {
        return value == min || value == max ? 1 : 0;
    }
}
