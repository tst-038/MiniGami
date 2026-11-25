package io.github.tst_038.minigami.core.stat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatTest {

    @Test
    void onlyWinsAndPlayedAreKeptByMiniGami() {
        assertTrue(new Stat(Stat.WINS).keptByMiniGami());
        assertTrue(new Stat(Stat.PLAYED).keptByMiniGami());
        assertFalse(Stat.POINTS.keptByMiniGami());
        assertFalse(new Stat("kills").keptByMiniGami());
    }
}
