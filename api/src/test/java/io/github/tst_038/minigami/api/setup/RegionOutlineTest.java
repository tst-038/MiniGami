package io.github.tst_038.minigami.api.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.region.Region;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RegionOutlineTest {

    @Test
    void aSingleBlockIsDrawnOnItsEightCornersOfTheOutsideOfTheBlock() {
        var points = RegionOutline.points(new Region("one", null, 3, 64, -2, 3, 64, -2));

        // Each of the 12 edges has its two ends; the corners are shared, so 8 distinct points.
        assertEquals(8, points.stream().map(Arrays::toString).distinct().count());
        assertTrue(points.stream().allMatch(at -> (at[0] == 3 || at[0] == 4) && (at[1] == 64 || at[1] == 65) && (at[2] == -2 || at[2] == -1)));
    }

    @Test
    void everyPointLiesOnAnEdgeOfTheBox() {
        var points = RegionOutline.points(new Region("hill", null, 0, 60, 0, 4, 63, 9));

        assertTrue(points.stream().allMatch(at -> {
            int onFaces = (at[0] == 0 || at[0] == 5 ? 1 : 0) + (at[1] == 60 || at[1] == 64 ? 1 : 0) + (at[2] == 0 || at[2] == 10 ? 1 : 0);
            return onFaces >= 2;
        }));
    }

    @Test
    void aHugeRegionStaysAtAboutAThousandPoints() {
        var points = RegionOutline.points(new Region("huge", null, -5000, -64, -5000, 5000, 300, 5000));

        assertTrue(points.size() < 1500, "was " + points.size());
    }
}
