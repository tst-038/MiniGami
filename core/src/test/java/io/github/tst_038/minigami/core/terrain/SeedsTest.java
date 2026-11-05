package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SeedsTest {

    @Test
    void sameSeedAndPathGiveTheSameResult() {
        assertEquals(Seeds.derive(42, "ores", 3, "diamond"), Seeds.derive(42, "ores", 3, "diamond"));
        assertEquals(Seeds.random(42, "a").nextLong(), Seeds.random(42, "a").nextLong());
    }

    @Test
    void anyChangeInSeedOrPathGivesAnotherResult() {
        long base = Seeds.derive(42, "ores", 3);
        assertNotEquals(base, Seeds.derive(43, "ores", 3));
        assertNotEquals(base, Seeds.derive(42, "trees", 3));
        assertNotEquals(base, Seeds.derive(42, "ores", 4));
        assertNotEquals(Seeds.derive(42, "a", "b"), Seeds.derive(42, "b", "a"));
    }

    @Test
    void manyNearbyPathsStayDistinct() {
        var seen = new HashSet<Long>();
        for (int area = 0; area < 4; area++) {
            for (int attempt = 0; attempt < 100; attempt++) {
                seen.add(Seeds.derive(1, area, attempt));
            }
        }
        assertEquals(400, seen.size());
    }

    @Test
    void integerAndLongPartsAgree() {
        assertEquals(Seeds.derive(7, 5), Seeds.derive(7, 5L));
    }

    @Test
    void refusesPartsItCannotMix() {
        assertThrows(IllegalArgumentException.class, () -> Seeds.derive(1, new Object()));
    }
}
