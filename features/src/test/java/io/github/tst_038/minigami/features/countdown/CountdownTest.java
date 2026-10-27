package io.github.tst_038.minigami.features.countdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.features.FakeMatch;
import java.util.List;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CountdownTest {

    private final FakeMatch match = new FakeMatch();

    CountdownTest() {
        match.texts.set("match.countdown", "<seconds>");
    }

    private List<String> shown() {
        return match.actionBars.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    @Test
    void countsDownTheLastSecondsOfThePhase() {
        match.phase = Phase.timed("fighting", 10 * 20);
        new Countdown(3).enable(match);

        match.tick(7 * 20 - 1);
        assertTrue(shown().isEmpty());

        match.tick(1);
        assertEquals(List.of("3"), shown());

        match.tick(2 * 20);
        assertEquals(List.of("3", "2", "1"), shown());
    }

    @Test
    void aPhaseShorterThanTheCountdownOnlyCountsTheSecondsAfterItsStart() {
        match.phase = Phase.timed("ending", 2 * 20);
        new Countdown(10).enable(match);

        match.tick(2 * 20);

        assertEquals(List.of("1"), shown());
    }

    @Test
    void stopsWhenThePhaseEndsEarly() {
        match.phase = Phase.timed("fighting", 10 * 20);
        new Countdown(3).enable(match);

        match.scope.close();
        match.tick(10 * 20);

        assertTrue(shown().isEmpty());
    }

    @Test
    void anUntimedPhaseHasNothingToCountDown() {
        new Countdown(3).enable(match);

        match.tick(1000);

        assertTrue(shown().isEmpty());
    }
}
