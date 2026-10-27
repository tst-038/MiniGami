package io.github.tst_038.minigami.core.win;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.Optional;

/** Decides when a match is over and who placed where. */
public interface WinCondition {

    /** Asked after every elimination and score change. Returns the result once the match is decided, empty until then. */
    Optional<MatchResult> check(Roster roster, Scores scores);

    /**
     * Asked when the match reaches its last phase, or runs out of phases, without {@link #check}
     * having decided it, for example when time is up: who placed where as things stand.
     */
    MatchResult finalResult(Roster roster, Scores scores);
}
