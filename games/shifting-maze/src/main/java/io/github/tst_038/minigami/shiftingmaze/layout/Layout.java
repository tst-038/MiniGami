package io.github.tst_038.minigami.shiftingmaze.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A grid of cells whose inner walls are open or closed. Outer walls are not walls here and never open. */
public final class Layout {
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final int columns;
    private final int rows;
    private final List<Cell> cells = new ArrayList<>();
    private final List<Wall> walls = new ArrayList<>();
    private final Map<Wall, Integer> index = new HashMap<>(); // lookup only, never iterated
    private final boolean[] open;

    public Layout(int columns, int rows) {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Size must be at least 1x1");
        }
        this.columns = columns;
        this.rows = rows;
        for (int x = 0; x < columns; x++) {
            for (int z = 0; z < rows; z++) {
                cells.add(new Cell(x, z));
            }
        }
        for (Cell c : cells) {
            if (c.x() + 1 < columns) {
                addWall(new Wall(c, new Cell(c.x() + 1, c.z())));
            }
            if (c.z() + 1 < rows) {
                addWall(new Wall(c, new Cell(c.x(), c.z() + 1)));
            }
        }
        open = new boolean[walls.size()];
    }

    private void addWall(Wall w) {
        index.put(w, walls.size());
        walls.add(w);
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public List<Cell> cells() {
        return List.copyOf(cells);
    }

    public List<Wall> walls() {
        return List.copyOf(walls);
    }

    public boolean isOpen(Wall w) {
        return open[indexOf(w)];
    }

    public int openCount() {
        int n = 0;
        for (boolean b : open) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    void open(Wall w) {
        open[indexOf(w)] = true;
    }

    void close(Wall w) {
        open[indexOf(w)] = false;
    }

    private int indexOf(Wall w) {
        Integer i = index.get(w);
        if (i == null) {
            throw new IllegalArgumentException("Not an inner wall of this layout: " + w);
        }
        return i;
    }

    public Layout copy() {
        Layout l = new Layout(columns, rows);
        System.arraycopy(open, 0, l.open, 0, open.length);
        return l;
    }

    /** Cells reachable in one step through an open wall, in fixed order. */
    public List<Cell> neighbours(Cell c) {
        List<Cell> out = new ArrayList<>(4);
        for (int[] s : STEPS) {
            int x = c.x() + s[0];
            int z = c.z() + s[1];
            if (x >= 0 && x < columns && z >= 0 && z < rows && open[indexOf(new Wall(c, new Cell(x, z)))]) {
                out.add(new Cell(x, z));
            }
        }
        return out;
    }

    /** Shortest path through open walls, as the walls crossed; empty if from equals to or if unreachable. */
    public List<Wall> path(Cell from, Cell to) {
        Map<Cell, Cell> parent = new HashMap<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        parent.put(from, from);
        queue.add(from);
        while (!queue.isEmpty() && !parent.containsKey(to)) {
            Cell c = queue.poll();
            for (Cell n : neighbours(c)) {
                if (parent.putIfAbsent(n, c) == null) {
                    queue.add(n);
                }
            }
        }
        List<Wall> path = new ArrayList<>();
        if (!parent.containsKey(to)) {
            return path;
        }
        for (Cell c = to; !c.equals(from); c = parent.get(c)) {
            path.addFirst(new Wall(parent.get(c), c));
        }
        return path;
    }

    public boolean isConnected() {
        for (int[] column : distances(new Cell(0, 0))) {
            for (int v : column) {
                if (v < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Steps from {@code from} to each cell as [x][z]; -1 where unreachable. */
    public int[][] distances(Cell from) {
        int[][] d = new int[columns][rows];
        for (int[] column : d) {
            Arrays.fill(column, -1);
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        d[from.x()][from.z()] = 0;
        queue.add(from);
        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            for (Cell n : neighbours(c)) {
                if (d[n.x()][n.z()] < 0) {
                    d[n.x()][n.z()] = d[c.x()][c.z()] + 1;
                    queue.add(n);
                }
            }
        }
        return d;
    }
}
