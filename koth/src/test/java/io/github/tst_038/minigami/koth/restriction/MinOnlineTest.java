package io.github.tst_038.minigami.koth.restriction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MinOnlineTest {

    @Test
    void enoughPlayersPass() {
        assertEquals(Optional.empty(), new MinOnline(3).check(new Situation(3)));
        assertEquals(Optional.empty(), new MinOnline(3).check(new Situation(10)));
    }

    @Test
    void tooFewAreRefusedWithBothNumbers() {
        assertEquals(Optional.of(new Refusal("restriction.min-online", Map.of("online", "2", "needed", "3"))),
                new MinOnline(3).check(new Situation(2)));
    }

    @Test
    void noOneAtAllIsRefused() {
        assertEquals(Optional.of(new Refusal("restriction.min-online", Map.of("online", "0", "needed", "1"))),
                new MinOnline(1).check(new Situation(0)));
    }

    @Test
    void aMinimumBelowOneMeansNoRestrictionAndIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MinOnline(0));
    }
}
