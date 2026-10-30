package io.github.tst_038.minigami.domination;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OwnershipTest {

    @Test
    void aTeamAloneTakesThePoint() {
        assertEquals(Optional.of("red"), Ownership.next(Optional.of("blue"), List.of("red"), false));
        assertEquals(Optional.of("red"), Ownership.next(Optional.of("blue"), List.of("red"), true));
    }

    @Test
    void aKeptPointStaysWithItsOwnerWhenEmptyOrContested() {
        assertEquals(Optional.of("blue"), Ownership.next(Optional.of("blue"), List.of(), true));
        assertEquals(Optional.of("blue"), Ownership.next(Optional.of("blue"), List.of("red", "blue"), true));
    }

    @Test
    void aPointNotKeptIsOwnedOnlyWhileStoodOnAlone() {
        assertEquals(Optional.empty(), Ownership.next(Optional.of("blue"), List.of(), false));
        assertEquals(Optional.empty(), Ownership.next(Optional.of("blue"), List.of("red", "blue"), false));
    }
}
