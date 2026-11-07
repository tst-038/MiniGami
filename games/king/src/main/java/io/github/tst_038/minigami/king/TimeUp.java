package io.github.tst_038.minigami.king;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;

/** Tells everyone that time, not the last fall, decided the match, when both teams still have someone alive. */
final class TimeUp implements Feature {

    @Override
    public void enable(MatchContext context) {
        if (context.roster().teamsLeft().size() > 1) {
            context.messages().send(context.audience(), "king.time-up");
        }
    }
}
