package io.github.tst_038.minigami.core.team;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Hands out ranks inside each team on its own, at random, by the same shares as roles: a rank with a fixed number gets that many
 * in every team, and the rest rank gets whoever is left. A team too small for every rank fills the ranks in order and leaves the
 * rest rank at least one member, so it may have none of the later ranks.
 */
public final class RandomRanks {

    private final RandomRoles roles;

    public RandomRanks(RandomGenerator random) {
        this.roles = new RandomRoles(random);
    }

    /**
     * @param teams the members of each team
     * @param ranks the rank ids, in the order of {@code shares}
     * @param shares one per rank, one of them the rest, so every member gets a rank
     * @return the rank of each member
     * @throws IllegalArgumentException if there is not a share for each rank, or no share is the rest
     */
    public Map<UUID, String> deal(Collection<? extends List<UUID>> teams, List<String> ranks, List<RoleShare> shares) {
        if (ranks.size() != shares.size() || shares.stream().noneMatch(RoleShare::isRest)) {
            throw new IllegalArgumentException("Needs a share for each rank and one rest rank, got " + ranks + " and " + shares);
        }
        var dealt = new HashMap<UUID, String>();
        for (var team : teams) {
            var perRank = roles.deal(team, shares);
            for (int i = 0; i < ranks.size(); i++) {
                for (var member : perRank.get(i)) {
                    dealt.put(member, ranks.get(i));
                }
            }
        }
        return dealt;
    }
}
