package io.github.tst_038.minigami.core.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TerritoryTest {

    private final UUID red = UUID.randomUUID();
    private final UUID blue = UUID.randomUUID();
    private final Territory territory = new Territory();

    @Test
    void paintingFreeGroundHasNoPreviousOwner() {
        assertEquals(Optional.empty(), territory.paint(Territory.pack(1, 64, 1), red));
        assertEquals(1, territory.count(red));
    }

    @Test
    void paintingOverGivesTheBlockAndNamesThePreviousOwner() {
        var position = Territory.pack(1, 64, 1);
        territory.paint(position, red);

        assertEquals(Optional.of(red), territory.paint(position, blue));

        assertEquals(0, territory.count(red));
        assertEquals(1, territory.count(blue));
    }

    @Test
    void paintingYourOwnBlockChangesNothing() {
        var position = Territory.pack(1, 64, 1);
        territory.paint(position, red);

        assertEquals(Optional.of(red), territory.paint(position, red));

        assertEquals(1, territory.count(red));
    }

    @Test
    void countsStayRightWhenBlocksChangeHandsBackAndForth() {
        for (int x = 0; x < 10; x++) {
            territory.paint(Territory.pack(x, 0, 0), red);
        }
        for (int x = 0; x < 4; x++) {
            territory.paint(Territory.pack(x, 0, 0), blue);
        }
        territory.paint(Territory.pack(0, 0, 0), red);

        assertEquals(7, territory.count(red));
        assertEquals(3, territory.count(blue));
    }

    @Test
    void manyBlocksAreCountedWithoutScanning() {
        for (int x = 0; x < 300; x++) {
            for (int z = 0; z < 300; z++) {
                territory.paint(Territory.pack(x, 70, z), red);
            }
        }

        assertEquals(90_000, territory.count(red));
    }

    @Test
    void negativeCoordinatesPackToDistinctPositions() {
        var positions = new HashSet<Long>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    positions.add(Territory.pack(x, y, z));
                }
            }
        }

        assertEquals(343, positions.size());
        assertNotEquals(Territory.pack(-1, 0, 0), Territory.pack(0, 0, -1));
        assertNotEquals(Territory.pack(0, -1, 0), Territory.pack(-1, 0, 0));
    }

    @Test
    void theWorldsLimitsPackToDistinctPositions() {
        territory.paint(Territory.pack(-30_000_000, -64, 29_999_999), red);

        assertEquals(Optional.empty(), territory.paint(Territory.pack(29_999_999, -64, -30_000_000), blue));
        assertEquals(Optional.of(red), territory.paint(Territory.pack(-30_000_000, -64, 29_999_999), blue));
    }

    @Test
    void resetLeavesNoOwners() {
        territory.paint(Territory.pack(1, 1, 1), red);

        territory.reset();

        assertEquals(0, territory.count(red));
        assertEquals(Optional.empty(), territory.paint(Territory.pack(1, 1, 1), blue));
    }
}
