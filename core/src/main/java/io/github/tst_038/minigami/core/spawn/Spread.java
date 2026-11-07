package io.github.tst_038.minigami.core.spawn;

import java.util.List;

/** Picks, of several places a player may start at, the one farthest from where the others are. */
public final class Spread {

    /** A position in a world, without saying which. */
    public record Point(double x, double y, double z) {

        double distanceSquared(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private Spread() {
    }

    /**
     * @param candidates the places to choose from, at least one
     * @param others where the other players are
     * @return the index of the candidate whose nearest other player is farthest away, the first of equals; the first when nobody else is there
     */
    public static int farthest(List<Point> candidates, List<Point> others) {
        int best = 0;
        double bestDistance = -1;
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            double nearest = others.stream().mapToDouble(candidate::distanceSquared).min().orElse(0);
            if (nearest > bestDistance) {
                best = i;
                bestDistance = nearest;
            }
        }
        return best;
    }
}
