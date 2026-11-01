package io.github.tst_038.minigami.shiftingmaze.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Where teams start, where the exit and the lairs are, and where each team's keys lie. */
public record Landmarks(List<Cell> starts, Cell exit, List<Cell> lairs, List<List<Cell>> keys) {

    /** {@code starts.get(i)} and {@code keys.get(i)} belong to team i; more than four teams share corners. */
    public static Landmarks place(Layout layout, int teams, int keysPerTeam, RandomGenerator random) {
        int mx = layout.columns() - 1;
        int mz = layout.rows() - 1;
        List<Cell> corners = List.of(new Cell(0, 0), new Cell(mx, mz), new Cell(mx, 0), new Cell(0, mz));
        List<Cell> starts = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            starts.add(corners.get(i % 4));
        }
        Cell exit = new Cell(layout.columns() / 2, layout.rows() / 2);
        List<Cell> lairs = List.of(
                new Cell(layout.columns() / 2, 0), new Cell(layout.columns() / 2, mz),
                new Cell(0, layout.rows() / 2), new Cell(mx, layout.rows() / 2));

        Set<Cell> taken = new HashSet<>(corners);
        taken.add(exit);
        taken.addAll(lairs);
        List<Cell> free = new ArrayList<>(layout.cells());
        free.removeAll(taken);
        Collections.shuffle(free, random);

        int each = teams == 0 ? 0 : Math.min(keysPerTeam, free.size() / teams);
        List<List<Cell>> keys = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            keys.add(List.copyOf(free.subList(i * each, (i + 1) * each)));
        }
        return new Landmarks(List.copyOf(starts), exit, lairs, List.copyOf(keys));
    }
}
