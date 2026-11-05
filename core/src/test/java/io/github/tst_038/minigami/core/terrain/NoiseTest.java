package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseTest {

    @Test
    void sameSeedGivesTheSameValues() {
        var a = new Noise(5);
        var b = new Noise(5);
        for (int i = 0; i < 50; i++) {
            assertEquals(a.sample(i * 0.37, i * 0.71), b.sample(i * 0.37, i * 0.71));
            assertEquals(a.sample(i * 0.37, i * 0.13, i * 0.71), b.sample(i * 0.37, i * 0.13, i * 0.71));
        }
    }

    @Test
    void otherSeedsGiveOtherValues() {
        assertNotEquals(new Noise(1).sample(3.3, 4.4), new Noise(2).sample(3.3, 4.4));
    }

    @Test
    void valuesStayInRangeAndUseMostOfIt() {
        var noise = new Noise(9);
        double low = 1;
        double high = -1;
        for (int x = 0; x < 200; x++) {
            for (int z = 0; z < 200; z++) {
                double value = noise.fractal(x * 0.05, z * 0.05, 4);
                assertTrue(value >= -1 && value <= 1, "out of range: " + value);
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
        }
        assertTrue(low < -0.3 && high > 0.3, "noise is too flat: " + low + " to " + high);
    }

    @Test
    void nearbyPointsAreNearbyValues() {
        var noise = new Noise(3);
        for (int i = 0; i < 100; i++) {
            double x = i * 0.9;
            assertTrue(Math.abs(noise.sample(x, 2.0) - noise.sample(x + 0.01, 2.0)) < 0.1);
        }
    }

    @Test
    void isZeroOnWholeNumbers() {
        assertEquals(0.0, new Noise(1).sample(4, 0.0, 7), 1e-12);
    }
}
