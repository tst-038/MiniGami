package io.github.tst_038.minigami.shiftingmaze.blocks;

import io.github.tst_038.minigami.shiftingmaze.layout.Cell;
import io.github.tst_038.minigami.shiftingmaze.layout.Wall;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a maze's cells, walls and posts lie in a region of blocks (corners included). The region's bottom layer is
 * the floor, the walls take the {@code wallHeight} layers above it and the ceiling, if any, the layer above those.
 * Along each axis a line of {@code wallThickness} blocks comes first, then a cell, a line, a cell, ... and a last line.
 */
public final class Geometry {
    private final int minX, minY, minZ, cellWidth, wallThickness, wallHeight;
    private final int maxX, maxZ;
    private final boolean ceiling;
    private final int columns, rows;
    private final boolean tooSmall;

    public Geometry(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                    int cellWidth, int wallThickness, int wallHeight, boolean ceiling) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.cellWidth = cellWidth;
        this.wallThickness = wallThickness;
        this.wallHeight = wallHeight;
        this.ceiling = ceiling;
        int pitch = cellWidth + wallThickness;
        this.columns = Math.max(0, (maxX - minX + 1 - wallThickness) / pitch);
        this.rows = Math.max(0, (maxZ - minZ + 1 - wallThickness) / pitch);
        int needed = wallHeight + 1 + (ceiling ? 1 : 0);
        this.tooSmall = columns < 2 || rows < 2 || maxY - minY + 1 < needed;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /** Less than 2x2 cells fit, or the region is too low for the walls (and ceiling). */
    public boolean tooSmall() {
        return tooSmall;
    }

    /** The whole bottom layer of the region. */
    public Span floor() {
        return new Span(minX, minY, minZ, maxX, minY, maxZ);
    }

    public Optional<Span> ceiling() {
        int y = minY + wallHeight + 1;
        return ceiling ? Optional.of(new Span(minX, y, minZ, maxX, y, maxZ)) : Optional.empty();
    }

    /** One post at every crossing of the wall lines, outer ones included: (columns + 1) * (rows + 1). */
    public List<Span> posts() {
        List<Span> posts = new ArrayList<>();
        for (int i = 0; i <= columns; i++) {
            for (int j = 0; j <= rows; j++) {
                posts.add(box(lineX(i), lineZ(j), wallThickness, wallThickness, wallHeight));
            }
        }
        return posts;
    }

    /** The outer walls, one span per cell edge on the border, between the posts. */
    public List<Span> boundary() {
        List<Span> border = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            border.add(box(cellX(i), lineZ(0), cellWidth, wallThickness, wallHeight));
            border.add(box(cellX(i), lineZ(rows), cellWidth, wallThickness, wallHeight));
        }
        for (int j = 0; j < rows; j++) {
            border.add(box(lineX(0), cellZ(j), wallThickness, cellWidth, wallHeight));
            border.add(box(lineX(columns), cellZ(j), wallThickness, cellWidth, wallHeight));
        }
        return border;
    }

    /** The span of the wall between two neighbouring cells. */
    public Span wall(Wall wall) {
        Cell a = wall.a();
        Cell b = wall.b();
        if (a.z() == b.z()) {
            return box(lineX(b.x()), cellZ(a.z()), wallThickness, cellWidth, wallHeight);
        }
        return box(cellX(a.x()), lineZ(b.z()), cellWidth, wallThickness, wallHeight);
    }

    /** The cell's footprint on the first layer above the floor. */
    public Span cell(Cell cell) {
        return box(cellX(cell.x()), cellZ(cell.z()), cellWidth, cellWidth, 1);
    }

    /** The cell the point is in, or empty over a wall, a post or outside the maze. */
    public Optional<Cell> cellAt(double x, double z) {
        int column = index(x, minX, columns);
        int row = index(z, minZ, rows);
        return column < 0 || row < 0 ? Optional.empty() : Optional.of(new Cell(column, row));
    }

    /** Whether the point lies over the cells within {@code radius} cells of {@code centre} (any direction) or the walls between them. */
    public boolean near(Cell centre, int radius, double x, double z) {
        int x0 = cellX(Math.max(0, centre.x() - radius));
        int x1 = cellX(Math.min(columns - 1, centre.x() + radius)) + cellWidth;
        int z0 = cellZ(Math.max(0, centre.z() - radius));
        int z1 = cellZ(Math.min(rows - 1, centre.z() + radius)) + cellWidth;
        return x >= x0 && x < x1 && z >= z0 && z < z1;
    }

    /** The middle of the cell, at the height of the first layer above the floor, as {x, y, z}. */
    public double[] standingSpot(Cell cell) {
        return new double[] {cellX(cell.x()) + cellWidth / 2.0, minY + 1, cellZ(cell.z()) + cellWidth / 2.0};
    }

    private int index(double at, int min, int count) {
        int rel = (int) Math.floor(at) - min;
        int pitch = cellWidth + wallThickness;
        int i = Math.floorDiv(rel, pitch);
        return Math.floorMod(rel, pitch) < wallThickness || i < 0 || i >= count ? -1 : i;
    }

    private int lineX(int i) {
        return minX + i * (cellWidth + wallThickness);
    }

    private int lineZ(int j) {
        return minZ + j * (cellWidth + wallThickness);
    }

    private int cellX(int i) {
        return lineX(i) + wallThickness;
    }

    private int cellZ(int j) {
        return lineZ(j) + wallThickness;
    }

    /** A box of {@code height} layers starting right over the floor. */
    private Span box(int x, int z, int sizeX, int sizeZ, int height) {
        return new Span(x, minY + 1, z, x + sizeX - 1, minY + height, z + sizeZ - 1);
    }
}
