package io.github.tst_038.minigami.core.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class FloorsTest {

    private final Floors floors = new Floors(List.of(70, 60, 60, 65));

    @Test
    void sameHeightsAreOneFloor() {
        assertEquals(3, floors.count());
    }

    @Test
    void aLevelIsTheHighestFloorAtOrBelowTheFeet() {
        // Counted from the top: the highest floor is level 1.
        assertEquals(OptionalInt.of(3), floors.levelAt(60));
        assertEquals(OptionalInt.of(3), floors.levelAt(64.9));
        assertEquals(OptionalInt.of(2), floors.levelAt(65));
        assertEquals(OptionalInt.of(1), floors.levelAt(90));
    }

    @Test
    void belowEveryFloorIsNoLevel() {
        assertEquals(OptionalInt.empty(), floors.levelAt(59.5));
        assertEquals(OptionalInt.empty(), new Floors(List.of()).levelAt(64));
    }
}
