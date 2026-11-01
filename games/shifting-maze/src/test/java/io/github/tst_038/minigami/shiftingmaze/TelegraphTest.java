package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegraphTest {

    private final FakeMatch match = new FakeMatch();
    private final List<String> log = new ArrayList<>();
    private final Telegraph telegraph = new Telegraph(List.of(new Cue() {
        @Override
        public void tick(MatchContext context, Span wall, boolean opening, int ticksLeft) {
            log.add((opening ? "open " : "close ") + wall.minX() + " " + ticksLeft);
        }

        @Override
        public void stop(MatchContext context, Span wall) {
            log.add("stop " + wall.minX());
        }
    }));

    private static Span wall(int x) {
        return new Span(x, 65, 0, x, 67, 2);
    }

    @Test
    void cuesPlayEveryIntervalWithTheTimeLeftUntilStopped() {
        telegraph.start(match, List.of(wall(1)), List.of(wall(5)), 20);

        assertEquals(List.of("open 1 20", "close 5 20"), log);
        match.tick(5);
        assertEquals(List.of("open 1 20", "close 5 20", "open 1 15", "close 5 15"), log);
        match.tick(10);
        assertEquals("close 5 5", log.getLast());
    }

    @Test
    void stopStopsEveryWallOnceAndEndsTheCues() {
        telegraph.start(match, List.of(wall(1), wall(9)), List.of(wall(5)), 60);
        log.clear();

        telegraph.stop(match);
        telegraph.stop(match);
        match.tick(60);

        assertEquals(List.of("stop 1", "stop 9", "stop 5"), log);
    }

    @Test
    void startingAgainStopsTheWallsOfTheLastRound() {
        telegraph.start(match, List.of(wall(1)), List.of(), 60);
        log.clear();

        telegraph.start(match, List.of(), List.of(wall(5)), 60);

        assertTrue(log.indexOf("stop 1") < log.indexOf("close 5 60"));
    }
}
