package io.github.tst_038.minigami.api.loot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MiniGamiSavedItemsTest {

    @Test
    void namesAreLowerCaseLettersDigitsUnderscoresAndDashes() {
        assertTrue(MiniGamiSavedItems.isValidName("koth-crate_key2"));
        assertFalse(MiniGamiSavedItems.isValidName(""));
        assertFalse(MiniGamiSavedItems.isValidName("Crate"));
        assertFalse(MiniGamiSavedItems.isValidName("crate key"));
        assertFalse(MiniGamiSavedItems.isValidName("../crate"));
    }
}
