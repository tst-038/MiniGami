package io.github.tst_038.minigami.paper.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.win.LastManStanding;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ActiveMatchesTest {

    private final List<LogRecord> logged = new ArrayList<>();
    private final ActiveMatches matches = new ActiveMatches(logger());

    private Logger logger() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    private Match startedMatch(Phase... phases) {
        var match = new Match(List.of(phases), new LastManStanding(), new PhaseListener() {});
        match.start();
        matches.add(match);
        return match;
    }

    @Test
    void ticksEveryActiveMatch() {
        var first = startedMatch(Phase.untimed("playing"));
        var second = startedMatch(Phase.untimed("playing"));

        matches.tick();
        matches.tick();

        assertEquals(2, first.clock().elapsedTicks());
        assertEquals(2, second.clock().elapsedTicks());
    }

    @Test
    void finishedMatchIsDisposedAndRemoved() {
        var match = startedMatch(Phase.timed("playing", 1));

        matches.tick();

        assertTrue(match.scope().isClosed());
        assertTrue(matches.all().isEmpty());
    }

    @Test
    void failingMatchDoesNotStopTheOthers() {
        var broken = startedMatch(Phase.untimed("playing"));
        broken.scope().register(broken.clock().repeat(1, () -> { throw new IllegalArgumentException("boom"); }));
        var healthy = startedMatch(Phase.untimed("playing"));

        matches.tick();

        assertEquals(1, healthy.clock().elapsedTicks());
        assertEquals(1, logged.size());
        assertEquals(2, matches.all().size());
    }

    @Test
    void disposeAllReleasesEveryMatch() {
        var first = startedMatch(Phase.untimed("playing"));
        var second = startedMatch(Phase.untimed("playing"));

        matches.disposeAll();

        assertTrue(first.scope().isClosed());
        assertTrue(second.scope().isClosed());
        assertTrue(matches.all().isEmpty());
    }
}
