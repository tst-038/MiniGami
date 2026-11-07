package io.github.tst_038.minigami.core.border;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BorderSizeTest {

    @Test
    void shrinksEvenlyThenStays() {
        assertEquals(200, BorderSize.at(200, 20, 100, 0));
        assertEquals(110, BorderSize.at(200, 20, 100, 50));
        assertEquals(20, BorderSize.at(200, 20, 100, 100));
        assertEquals(20, BorderSize.at(200, 20, 100, 500));
    }
}
