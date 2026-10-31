package io.github.tst_038.minigami.api.region;

import io.github.tst_038.minigami.core.roster.MatchTeam;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The arena region one team of a match plays in, shared by its members.
 *
 * @param team the team's id, or empty for a player without a team
 * @param members in join order, eliminated ones included
 */
public record TeamRegion(Region region, Optional<String> team, List<UUID> members) {

    public TeamRegion {
        members = List.copyOf(members);
    }

    /**
     * Hands out {@code <prefix>-1}, {@code <prefix>-2} and so on to the teams in their order; a player
     * without a team is a team of one and gets their own.
     *
     * @param regions the arena's regions by name
     * @throws IllegalStateException if the arena lacks a region for one of the teams
     */
    public static List<TeamRegion> assign(String prefix, List<MatchTeam> teams, Function<String, Optional<Region>> regions) {
        var assigned = new ArrayList<TeamRegion>();
        for (int i = 0; i < teams.size(); i++) {
            var name = prefix + "-" + (i + 1);
            var region = regions.apply(name).orElseThrow(() -> new IllegalStateException("The arena has no region " + name));
            assigned.add(new TeamRegion(region, teams.get(i).id(), teams.get(i).ids()));
        }
        return List.copyOf(assigned);
    }
}
