package io.github.tst_038.minigami.features.countdown;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/** Counts down the last seconds of a timed phase above everyone's hotbar; does nothing in an untimed one. */
public final class Countdown implements Feature {

    private final int seconds;

    public Countdown(int seconds) {
        this.seconds = seconds;
    }

    @Override
    public void enable(MatchContext context) {
        long duration = context.phase().durationTicks();
        for (int left = 1; left <= seconds && left * 20L < duration; left++) {
            int shown = left;
            context.scope().register(context.clock().schedule(duration - left * 20L, () -> context.audience()
                    .sendActionBar(context.messages().render("match.countdown", Formatter.number("seconds", shown)))));
        }
    }
}
