package io.github.tst_038.minigami.shiftingmaze.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class Generator {
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Generator() {}

    /** A random spanning tree, then loopPercent of the remaining closed walls opened. Same seed, same layout. */
    public static Layout generate(int columns, int rows, int loopPercent, RandomGenerator random) {
        Layout layout = new Layout(columns, rows);
        Set<Cell> seen = new HashSet<>(); // membership only, never iterated
        ArrayDeque<Cell> stack = new ArrayDeque<>();
        Cell start = new Cell(0, 0);
        seen.add(start);
        stack.push(start);
        while (!stack.isEmpty()) {
            Cell c = stack.peek();
            List<Cell> fresh = new ArrayList<>(4);
            for (int[] s : STEPS) {
                Cell n = new Cell(c.x() + s[0], c.z() + s[1]);
                if (n.x() >= 0 && n.x() < columns && n.z() >= 0 && n.z() < rows && !seen.contains(n)) {
                    fresh.add(n);
                }
            }
            if (fresh.isEmpty()) {
                stack.pop();
                continue;
            }
            Cell next = fresh.get(random.nextInt(fresh.size()));
            layout.open(new Wall(c, next));
            seen.add(next);
            stack.push(next);
        }
        List<Wall> closed = new ArrayList<>();
        for (Wall w : layout.walls()) {
            if (!layout.isOpen(w)) {
                closed.add(w);
            }
        }
        Collections.shuffle(closed, random);
        long extra = Math.round(loopPercent * closed.size() / 100.0);
        for (int i = 0; i < extra; i++) {
            layout.open(closed.get(i));
        }
        return layout;
    }
}
