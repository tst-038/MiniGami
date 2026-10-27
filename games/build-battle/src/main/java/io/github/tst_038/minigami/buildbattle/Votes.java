package io.github.tst_038.minigami.buildbattle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The score each voter gave each build; voting again on the same build replaces the earlier score. */
final class Votes {

    private final Map<UUID, Map<UUID, Integer>> byBuilder = new HashMap<>();

    /** Records the vote and returns how much the builder's total changes by it. */
    int vote(UUID builder, UUID voter, int score) {
        var earlier = byBuilder.computeIfAbsent(builder, id -> new HashMap<>()).put(voter, score);
        return earlier == null ? score : score - earlier;
    }
}
