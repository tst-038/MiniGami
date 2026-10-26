package io.github.tst_038.minigami.core;

import java.util.List;
import java.util.UUID;

/** One place in a {@link MatchResult}. Several participants share a place on a tie or as a team. */
public record Placement(int place, List<UUID> participants) {

    public Placement {
        if (place < 1) {
            throw new IllegalArgumentException("Place must be at least 1, got " + place);
        }
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("A placement needs at least one participant");
        }
        participants = List.copyOf(participants);
    }
}
