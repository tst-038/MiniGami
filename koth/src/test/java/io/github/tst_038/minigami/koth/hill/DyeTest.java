package io.github.tst_038.minigami.koth.hill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DyeTest {

    @Test
    void thereAreSixteenColours() {
        assertEquals(16, Dye.values().length);
    }

    @Test
    void aRandomOtherColourIsNeverTheCurrentOne() {
        var random = new Random(1);
        for (int i = 0; i < 200; i++) {
            assertNotEquals(Dye.RED, Dye.randomOther(Optional.of(Dye.RED), random));
        }
    }

    @Test
    void withNoCurrentColourEveryColourCanCome() {
        var random = new Random(1);
        var seen = EnumSet.noneOf(Dye.class);
        for (int i = 0; i < 500; i++) {
            seen.add(Dye.randomOther(Optional.empty(), random));
        }
        assertEquals(16, seen.size());
    }
}
