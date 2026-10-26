package io.github.tst_038.minigami.core;

import java.util.List;

/** How a match ended, as data: the placements, best first. Stats and rewards are derived from this. */
public record MatchResult(List<Placement> placements) {

    public MatchResult {
        placements = List.copyOf(placements);
    }
}
