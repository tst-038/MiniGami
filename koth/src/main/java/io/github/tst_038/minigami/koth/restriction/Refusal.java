package io.github.tst_038.minigami.koth.restriction;

import java.util.Map;

/** Why a start was refused: a key under {@code restriction.} in the messages, and the values for its placeholders. */
public record Refusal(String messageKey, Map<String, String> values) {

    public Refusal {
        values = Map.copyOf(values);
    }
}
