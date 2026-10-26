package io.github.tst_038.minigami.core.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchResultTest {

    private final UUID alice = UUID.randomUUID();

    @Test
    void placementRejectsPlaceBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new Placement(0, List.of(alice)));
    }

    @Test
    void placementRejectsEmptyParticipants() {
        assertThrows(IllegalArgumentException.class, () -> new Placement(1, List.of()));
    }

    @Test
    void resultIsACopyOfThePlacementsGiven() {
        var placements = new ArrayList<>(List.of(new Placement(1, List.of(alice))));
        var result = new MatchResult(placements);

        placements.clear();

        assertEquals(1, result.placements().size());
        assertThrows(UnsupportedOperationException.class, () -> result.placements().clear());
    }
}
