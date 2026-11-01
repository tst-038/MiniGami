package io.github.tst_038.minigami.shiftingmaze.build;

/** A box of whole blocks, both corners included. */
public record Span(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public int blocks() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Whether a box (corners in world coordinates) touches this span, with the span grown by {@code grow} on every side. */
    public boolean overlaps(double x0, double y0, double z0, double x1, double y1, double z1, double grow) {
        return x1 > minX - grow && x0 < maxX + 1 + grow
                && y1 > minY - grow && y0 < maxY + 1 + grow
                && z1 > minZ - grow && z0 < maxZ + 1 + grow;
    }

    /** The middle of the span as {x, y, z}. */
    public double[] centre() {
        return new double[] {(minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0};
    }
}
