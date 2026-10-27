package io.github.tst_038.minigami.core.roster;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Players who win or lose together. A participant without a team plays as a team of one.
 *
 * @param id empty for a team of one
 * @param members in join order, eliminated ones included
 */
public record MatchTeam(Optional<String> id, List<Participant> members) {

    public MatchTeam {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("A team needs at least one member");
        }
        members = List.copyOf(members);
    }

    /** Out once none of its members is alive. */
    public boolean isOut() {
        return members.stream().noneMatch(Participant::isAlive);
    }

    public List<UUID> ids() {
        return members.stream().map(Participant::id).toList();
    }
}
