package io.github.tst_038.minigami.shiftingmaze.layout;

/** The wall between two neighbouring cells, normalised so that a sorts before b (x, then z). */
public record Wall(Cell a, Cell b) {
    public Wall {
        if (Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) != 1) {
            throw new IllegalArgumentException("Not neighbours: " + a + " " + b);
        }
        if (a.x() > b.x() || (a.x() == b.x() && a.z() > b.z())) {
            Cell t = a;
            a = b;
            b = t;
        }
    }
}
