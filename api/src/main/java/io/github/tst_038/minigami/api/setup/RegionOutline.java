package io.github.tst_038.minigami.api.setup;

import io.github.tst_038.minigami.api.region.Region;
import java.util.ArrayList;
import java.util.List;

/** Where to put particles to draw a region's edges. */
public final class RegionOutline {

    private RegionOutline() {
    }

    /**
     * The points along the twelve edges, on the outside of the blocks, spaced so a big region stays at about a thousand points.
     *
     * @return {@code {x, y, z}} each
     */
    public static List<double[]> points(Region region) {
        double[] min = {region.minX(), region.minY(), region.minZ()};
        double[] max = {region.maxX() + 1, region.maxY() + 1, region.maxZ() + 1};
        var step = Math.max(1, 4 * (max[0] - min[0] + max[1] - min[1] + max[2] - min[2]) / 1000);
        var points = new ArrayList<double[]>();
        for (int corner = 0; corner < 8; corner++) {
            for (int axis = 0; axis < 3; axis++) {
                if ((corner & 1 << axis) != 0) {
                    continue;
                }
                double[] at = new double[3];
                for (int i = 0; i < 3; i++) {
                    at[i] = (corner & 1 << i) == 0 ? min[i] : max[i];
                }
                for (var along = min[axis]; along <= max[axis]; along += step) {
                    at[axis] = along;
                    points.add(at.clone());
                }
            }
        }
        return points;
    }
}
