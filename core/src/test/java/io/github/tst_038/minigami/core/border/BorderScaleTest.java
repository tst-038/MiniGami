package io.github.tst_038.minigami.core.border;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BorderScaleTest {

    @Test
    void sameScaleChangesNothing() {
        assertEquals(200, BorderScale.toWorld(200, 1, 1));
    }

    @Test
    void anOverworldArenaIsAnEighthAsBigInTheNether() {
        assertEquals(25, BorderScale.toWorld(200, 1, 8));
    }

    @Test
    void aNetherArenaIsEightTimesAsBigInTheOverworld() {
        assertEquals(1600, BorderScale.toWorld(200, 8, 1));
    }
}
