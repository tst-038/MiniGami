package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.core.team.GroupedTeams;
import io.github.tst_038.minigami.core.team.TeamFormation;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * How a match is opened by whoever asks for it, such as {@code /mg join} or a game night.
 *
 * @param isPublic whether anyone may join it from the list; a private match only gets the players its opener puts in
 * @param teams splits the players into teams at the start, for games with teams
 */
public record Opening(boolean isPublic, TeamFormation teams) {

    /** Open to anyone, with teams dealt out at random except that each party stays in one team. */
    public static Opening publicMatch(Supplier<List<Set<UUID>>> parties) {
        return new Opening(true, new GroupedTeams(parties, RandomGenerator.getDefault()));
    }
}
