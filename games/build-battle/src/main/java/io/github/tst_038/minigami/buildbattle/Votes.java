package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The votes on each builder's plot: one score per voter, changed by voting again. */
final class Votes {

    private final Map<UUID, Map<UUID, Integer>> byBuilder = new HashMap<>();

    void vote(UUID builder, UUID voter, int score) {
        byBuilder.computeIfAbsent(builder, id -> new HashMap<>()).put(voter, score);
    }

    int total(UUID builder) {
        return byBuilder.getOrDefault(builder, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Highest total first; equal totals share a place and the places after skip ahead (1, 1, 3). */
    MatchResult result(List<UUID> builders) {
        var ranked = builders.stream().sorted(Comparator.comparingInt(this::total).reversed()).toList();
        var placements = new ArrayList<Placement>();
        for (int i = 0; i < ranked.size(); ) {
            var total = total(ranked.get(i));
            var tied = new ArrayList<UUID>();
            var place = i + 1;
            for (; i < ranked.size() && total(ranked.get(i)) == total; i++) {
                tied.add(ranked.get(i));
            }
            placements.add(new Placement(place, tied));
        }
        return new MatchResult(placements);
    }
}
