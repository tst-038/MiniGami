package io.github.tst_038.minigami.core.win;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import java.util.Optional;

/** Decides when a match is over and who placed where. */
public interface WinCondition {

    /** Called after every elimination. Returns the result once the match is decided, empty until then. */
    Optional<MatchResult> check(Roster roster);
}
