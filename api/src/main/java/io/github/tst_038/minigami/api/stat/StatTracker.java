package io.github.tst_038.minigami.api.stat;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.stat.Stat;

/**
 * Counts one stat, such as kills, from when play starts until the match ends. It only watches the
 * match and never changes how it plays; that is what features are for.
 *
 * <p>Trackers are shared by every match of a game, so they keep no match state themselves.
 */
public interface StatTracker {

    Stat stat();

    /** Subscribe through the context and count with {@link MatchContext#count}; released when the match ends. */
    void track(MatchContext context);
}
