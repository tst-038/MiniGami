package io.github.tst_038.minigami.api.stat;

import io.github.tst_038.minigami.core.stat.Stat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Related stats a mode keeps together under one id, e.g. {@code stats: [combat]} for kills and deaths.
 */
public record StatGroup(String id, List<StatTracker> trackers) {

    public StatGroup {
        trackers = List.copyOf(trackers);
    }

    public Set<Stat> stats() {
        return trackers.stream().map(StatTracker::stat).collect(Collectors.toUnmodifiableSet());
    }
}
