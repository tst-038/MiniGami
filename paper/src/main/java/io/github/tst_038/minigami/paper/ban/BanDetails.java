package io.github.tst_038.minigami.paper.ban;

import io.github.tst_038.minigami.api.ban.MiniGamiBans;
import io.github.tst_038.minigami.core.time.Durations;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What {@code /mga ban <players>} was given after the players: {@code [scope] [length] [reason...]}. The scope is {@code all}
 * when the text starts with a length or is empty; a first word that is neither a scope nor a length is an unknown scope.
 */
record BanDetails(String scope, Optional<Duration> length, Optional<String> reason) {

    /** The details, or empty if the first word is neither a scope there is nor a length: see {@link #firstWord}. */
    static Optional<BanDetails> parse(String text, Predicate<String> isScope) {
        var words = text.strip().split("\\s+", 2);
        var first = words[0];
        if (first.isEmpty()) {
            return Optional.of(new BanDetails(MiniGamiBans.ALL, Optional.empty(), Optional.empty()));
        }
        String scope;
        String rest;
        if (isScope.test(first)) {
            scope = first;
            rest = words.length > 1 ? words[1] : "";
        } else if (Durations.parse(first).isPresent()) {
            scope = MiniGamiBans.ALL;
            rest = text.strip();
        } else {
            return Optional.empty();
        }
        var next = rest.split("\\s+", 2);
        var length = Durations.parse(next[0]);
        var reason = (length.isPresent() ? (next.length > 1 ? next[1] : "") : rest).strip();
        return Optional.of(new BanDetails(scope, length, reason.isEmpty() ? Optional.empty() : Optional.of(reason)));
    }

    /** The first word of the text, to name an unknown scope. */
    static String firstWord(String text) {
        return text.strip().split("\\s+", 2)[0];
    }
}
