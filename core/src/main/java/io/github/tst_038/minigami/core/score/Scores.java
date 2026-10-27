package io.github.tst_038.minigami.core.score;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Points per participant; everyone starts at 0. */
public final class Scores {

    private final Map<UUID, Integer> points = new HashMap<>();

    /** Adds points, or takes them away when negative. */
    public void add(UUID id, int points) {
        this.points.merge(id, points, Integer::sum);
    }

    public int of(UUID id) {
        return points.getOrDefault(id, 0);
    }
}
