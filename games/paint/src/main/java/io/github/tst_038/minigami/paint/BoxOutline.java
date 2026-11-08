package io.github.tst_038.minigami.paint;

import java.util.ArrayList;
import java.util.List;

/** The points along the twelve edges of an axis-aligned box, for drawing it with particles. */
final class BoxOutline {

    private BoxOutline() {
    }

    /**
     * The 8 corners plus {@code segments - 1} points inside each edge, so {@code 8 + 12 * (segments - 1)}
     * points, none twice. {@code max} corners are the far corners (a block at x is from x to x + 1).
     */
    static List<double[]> points(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int segments) {
        var min = new double[] {minX, minY, minZ};
        var max = new double[] {maxX, maxY, maxZ};
        var points = new ArrayList<double[]>();
        for (int corner = 0; corner < 8; corner++) {
            points.add(new double[] {pick(min, max, 0, corner), pick(min, max, 1, corner), pick(min, max, 2, corner)});
        }
        for (int axis = 0; axis < 3; axis++) {
            for (int corner = 0; corner < 8; corner++) {
                if ((corner >> axis & 1) != 0) {
                    continue; // each edge once, from its low end
                }
                for (int i = 1; i < segments; i++) {
                    var point = new double[] {pick(min, max, 0, corner), pick(min, max, 1, corner), pick(min, max, 2, corner)};
                    point[axis] = min[axis] + (max[axis] - min[axis]) * i / segments;
                    points.add(point);
                }
            }
        }
        return points;
    }

    private static double pick(double[] min, double[] max, int axis, int corner) {
        return (corner >> axis & 1) == 0 ? min[axis] : max[axis];
    }
}
