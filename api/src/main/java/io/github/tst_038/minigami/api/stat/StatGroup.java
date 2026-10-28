package io.github.tst_038.minigami.api.stat;

import io.github.tst_038.minigami.core.stat.Stat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Related stats a game keeps together under one id, e.g. {@code stats: [combat]} for kills and deaths.
 */
public interface StatGroup {

    /** The key that selects this group in a game's {@code stats} list, e.g. {@code "combat"}. */
    String id();

    /** One tracker per stat, shared by every match of the games that keep the group. */
    List<StatTracker> trackers();

    default Set<Stat> stats() {
        return trackers().stream().map(StatTracker::stat).collect(Collectors.toUnmodifiableSet());
    }
}
