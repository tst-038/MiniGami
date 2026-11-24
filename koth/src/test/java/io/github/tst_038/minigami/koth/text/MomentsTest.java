package io.github.tst_038.minigami.koth.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MomentsTest {

    @Test
    void aMomentIsWrittenAsDayAndTimeInEnglish() {
        assertEquals("Mon 20:05", Moments.format(LocalDateTime.of(2026, 11, 23, 20, 5)));
    }
}
