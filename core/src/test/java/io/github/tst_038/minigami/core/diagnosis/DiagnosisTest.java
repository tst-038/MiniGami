package io.github.tst_038.minigami.core.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiagnosisTest {

    private static final Check<Integer> NEEDS_TWO = (spawns, diagnosis) -> {
        if (spawns < 2) {
            diagnosis.blocking("few", Map.of("spawns", String.valueOf(spawns)), "/add");
        }
    };
    private static final Check<Integer> LIKES_FOUR = (spawns, diagnosis) -> {
        if (spawns < 4) {
            diagnosis.warning("some", Map.of(), "/add");
        }
    };

    @Test
    void everyCheckRunsInOrderAndItsFindingsNameTheGameAndArena() {
        var diagnosis = Diagnosis.run("spleef duos", "winter", 1, List.of(NEEDS_TWO, LIKES_FOUR));

        assertEquals(List.of(
                new Finding("few", Severity.BLOCKING, "spleef duos", "winter", Map.of("spawns", "1"), Optional.of("/add")),
                new Finding("some", Severity.WARNING, "spleef duos", "winter", Map.of(), Optional.of("/add"))), diagnosis.findings());
        assertFalse(diagnosis.isReady());
    }

    @Test
    void warningsAloneLeaveTheArenaReady() {
        var diagnosis = Diagnosis.run("spleef", "winter", 3, List.of(NEEDS_TWO, LIKES_FOUR));

        assertTrue(diagnosis.isReady());
        assertEquals(1, diagnosis.findings().size());
    }

    @Test
    void nothingFoundIsReady() {
        var diagnosis = Diagnosis.run("spleef", "winter", 4, List.of(NEEDS_TWO, LIKES_FOUR));

        assertTrue(diagnosis.isReady());
        assertTrue(diagnosis.findings().isEmpty());
    }

    @Test
    void aBlockingFindingWithoutAFixHasNone() {
        var diagnosis = Diagnosis.run("spleef", "winter", 0, List.<Check<Integer>>of((spawns, found) -> found.blocking("broken", Map.of())));

        assertEquals(Optional.empty(), diagnosis.findings().getFirst().fix());
    }

    @Test
    void aFindingsTextIsNamedByItsCode() {
        var finding = new Finding("missing-region", Severity.BLOCKING, "the-wall", "castle", Map.of("region", "quarter-3"), Optional.empty());

        assertEquals("diagnosis.missing-region", finding.messageKey());
        assertTrue(finding.isBlocking());
        assertThrows(IllegalArgumentException.class, () -> new Finding(" ", Severity.WARNING, "a", "b", Map.of(), Optional.empty()));
    }
}
