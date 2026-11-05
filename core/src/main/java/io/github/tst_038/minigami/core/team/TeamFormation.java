package io.github.tst_038.minigami.core.team;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** How the players of a match are split into teams when it starts. */
public interface TeamFormation {

    /**
     * Splits every player over {@code count} teams. Teams may be empty when there are fewer players
     * than teams.
     *
     * @return exactly {@code count} teams
     * @throws IllegalArgumentException if {@code count} is less than 1
     */
    List<List<UUID>> form(List<UUID> players, int count);

    /** The players who go together, such as a party; roles are dealt so that each group stays on one role. None by default. */
    default List<? extends Collection<UUID>> groups() {
        return List.of();
    }
}
