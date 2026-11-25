package io.github.tst_038.minigami.api.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegacyColoursTest {

    @Test
    void codesBecomeTags() {
        assertEquals("<white>«❄» <red><bold>Yuletide Crate <reset><white>«❄»", LegacyColours.toMiniMessage("&f«❄» &c&lYuletide Crate &f«❄»"));
        assertEquals("<gold>Night", LegacyColours.toMiniMessage("§6Night"));
        assertEquals("<#ff8800>Sun<reset>set", LegacyColours.toMiniMessage("&#FF8800Sun&rset"));
        assertEquals("<green>A", LegacyColours.toMiniMessage("&AA"));
    }

    @Test
    void otherTextIsKept() {
        assertEquals("<gold>Tom & Jerry", LegacyColours.toMiniMessage("<gold>Tom & Jerry"));
        assertEquals("100&", LegacyColours.toMiniMessage("100&"));
    }

    @Test
    void knowsALeadingCode() {
        assertTrue(LegacyColours.startsWithCode(" &cRed"));
        assertFalse(LegacyColours.startsWithCode("Red &c"));
        assertFalse(LegacyColours.startsWithCode("&z"));
    }
}
