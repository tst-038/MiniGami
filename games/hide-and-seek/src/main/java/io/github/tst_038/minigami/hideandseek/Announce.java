package io.github.tst_038.minigami.hideandseek;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/** Tells the whole match a message when the phase starts, with the phase's length as {@code <seconds>}. */
record Announce(String key) implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.messages().send(context.audience(), key, Formatter.number("seconds", context.phase().durationTicks() / 20));
    }
}
