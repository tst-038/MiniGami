package io.github.tst_038.minigami.domination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CaptureTest {

    private static Capture<String> ownedByBlue() {
        var capture = new Capture<String>(3, true);
        capture.tick(List.of("blue"));
        capture.tick(List.of("blue"));
        capture.tick(List.of("blue"));
        return capture;
    }

    @Test
    void aTeamAloneForTheCaptureTimeTakesIt() {
        var capture = new Capture<String>(3, true);

        assertFalse(capture.tick(List.of("red")));
        assertFalse(capture.tick(List.of("red")));
        assertEquals(Optional.of("red"), capture.taker());
        assertTrue(capture.tick(List.of("red")));
        assertEquals(Optional.of("red"), capture.owner());
        assertEquals(Optional.empty(), capture.taker());
    }

    @Test
    void oneSecondTakesItAtOnce() {
        var capture = new Capture<String>(1, true);

        assertTrue(capture.tick(List.of("red")));
    }

    @Test
    void theOwnerKeepsItWhenItIsLeftEmpty() {
        var capture = ownedByBlue();

        capture.tick(List.of());

        assertEquals(Optional.of("blue"), capture.owner());
    }

    @Test
    void contestingPausesTheTaking() {
        var capture = ownedByBlue();
        capture.tick(List.of("red"));

        capture.tick(List.of("red", "blue"));

        assertEquals(1, capture.progress());
        capture.tick(List.of("red"));
        assertTrue(capture.tick(List.of("red")));
    }

    @Test
    void leavingItEmptyOrTheOwnerAloneStartsTheTakingOver() {
        var capture = ownedByBlue();
        capture.tick(List.of("red"));
        capture.tick(List.of());
        assertEquals(0, capture.progress());

        capture.tick(List.of("red"));
        capture.tick(List.of("blue"));
        assertEquals(Optional.empty(), capture.taker());
    }

    @Test
    void anotherTeamStartsItsOwnTaking() {
        var capture = new Capture<String>(3, true);
        capture.tick(List.of("red"));
        capture.tick(List.of("red"));

        capture.tick(List.of("green"));

        assertEquals(Optional.of("green"), capture.taker());
        assertEquals(1, capture.progress());
    }

    @Test
    void aPointNotKeptIsOwnedOnlyWhileItsOwnerStandsOnItAlone() {
        var capture = new Capture<String>(1, false);
        capture.tick(List.of("blue"));

        capture.tick(List.of("blue", "red"));
        assertEquals(Optional.empty(), capture.owner());

        capture.tick(List.of("blue"));
        capture.tick(List.of());
        assertEquals(Optional.empty(), capture.owner());
    }
}
