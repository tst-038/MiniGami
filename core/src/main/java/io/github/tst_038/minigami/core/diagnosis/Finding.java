package io.github.tst_038.minigami.core.diagnosis;

import java.util.Map;
import java.util.Optional;

/**
 * One thing a {@link Check} found about a game in an arena, such as a region the game needs that the
 * arena lacks.
 *
 * @param code what is wrong, stable across versions, such as {@code missing-region}; it names the text too
 * @param game the game it concerns, or game and variant, such as {@code spleef duos}
 * @param arena the arena it concerns
 * @param values the details its text fills in, such as {@code region} = {@code quarter-3}
 * @param fix the command that fixes it, if one does
 */
public record Finding(String code, Severity severity, String game, String arena, Map<String, String> values, Optional<String> fix) {

    /** Where the texts of findings are kept: {@code diagnosis.<code>}. */
    public static final String MESSAGES = "diagnosis.";

    public Finding {
        if (code.isBlank()) {
            throw new IllegalArgumentException("A finding needs a code");
        }
        values = Map.copyOf(values);
    }

    public boolean isBlocking() {
        return severity == Severity.BLOCKING;
    }

    /** The key of the text that says what is wrong, filled in with {@link #values}. */
    public String messageKey() {
        return MESSAGES + code;
    }
}
