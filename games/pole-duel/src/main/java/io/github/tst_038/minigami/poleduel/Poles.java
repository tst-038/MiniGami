package io.github.tst_038.minigami.poleduel;

import io.github.tst_038.minigami.core.spawn.Spread;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Works out where the poles stand: spread at random over an area, each at least a minimum distance from every other.
 * Pure and seeded, so the same seed in the same area gives the same poles.
 */
final class Poles {

    /** A column of blocks, by its place on the ground. */
    record Column(int x, int z) {

        private Spread.Point point() {
            return new Spread.Point(x, 0, z);
        }
    }

    /** The columns of an area, both ends included. */
    record Bounds(int minX, int minZ, int maxX, int maxZ) {

        Bounds {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("The minimum corner lies beyond the maximum");
            }
        }
    }

    /** How many columns are drawn for each pole; the one farthest from the poles so far wins. */
    private static final int DRAWS = 24;

    private Poles() {
    }

    /** As {@link #place(Bounds, int, int, Random, Predicate)} with every column allowed. */
    static List<Column> place(Bounds bounds, int count, int minDistance, Random random) {
        return place(bounds, count, minDistance, random, column -> true);
    }

    /**
     * Picks columns one at a time from those far enough from the poles so far, drawing a few at random and taking the one
     * farthest from the rest, so the poles are spread without lining up. Should that run out of room before every pole stands,
     * the poles go on a grid with the minimum distance as its step, which fits more closely.
     *
     * @param minDistance the least distance between two poles, in blocks
     * @param allowed which columns a pole may stand on
     * @return {@code count} columns in random order; empty when the area has no room for that many
     * @throws IllegalArgumentException if {@code count} or {@code minDistance} is less than 1
     */
    static List<Column> place(Bounds bounds, int count, int minDistance, Random random, Predicate<Column> allowed) {
        if (count < 1 || minDistance < 1) {
            throw new IllegalArgumentException("Needs at least one pole and a distance of at least 1 block");
        }
        var columns = new ArrayList<Column>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                var column = new Column(x, z);
                if (allowed.test(column)) {
                    columns.add(column);
                }
            }
        }
        var chosen = new ArrayList<Column>();
        while (chosen.size() < count) {
            var open = columns.stream().filter(column -> apart(chosen, column, minDistance)).toList();
            if (open.isEmpty()) {
                break;
            }
            var draws = random.ints(DRAWS, 0, open.size()).mapToObj(open::get).toList();
            chosen.add(draws.get(Spread.farthest(draws.stream().map(Column::point).toList(), chosen.stream().map(Column::point).toList())));
        }
        return chosen.size() == count ? chosen : grid(bounds, count, minDistance, random, allowed);
    }

    private static List<Column> grid(Bounds bounds, int count, int minDistance, Random random, Predicate<Column> allowed) {
        var spots = new ArrayList<Column>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x += minDistance) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z += minDistance) {
                var column = new Column(x, z);
                if (allowed.test(column)) {
                    spots.add(column);
                }
            }
        }
        if (spots.size() < count) {
            return List.of();
        }
        Collections.shuffle(spots, random);
        return List.copyOf(spots.subList(0, count));
    }

    private static boolean apart(List<Column> poles, Column column, int minDistance) {
        long least = (long) minDistance * minDistance;
        return poles.stream().allMatch(pole -> {
            long dx = pole.x() - column.x();
            long dz = pole.z() - column.z();
            return dx * dx + dz * dz >= least;
        });
    }
}
