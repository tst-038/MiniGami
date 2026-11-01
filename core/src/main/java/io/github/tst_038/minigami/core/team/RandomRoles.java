package io.github.tst_038.minigami.core.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Shuffles the players and gives each role its share, in order. The rest role gets whoever is left,
 * and always at least one player when there is anyone, so a role that asks for too many gets fewer.
 */
public final class RandomRoles {

    private final RandomGenerator random;

    public RandomRoles(RandomGenerator random) {
        this.random = random;
    }

    /**
     * @return the players of each role, in the order of {@code shares}; without a rest share one more, last list holds the
     *         players left over (at least one when there is anyone)
     * @throws IllegalArgumentException if more than one share is the rest
     */
    public List<List<UUID>> deal(List<UUID> players, List<RoleShare> shares) {
        if (shares.stream().filter(RoleShare::isRest).count() > 1) {
            throw new IllegalArgumentException("Needs at most one rest role, got " + shares);
        }
        var shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, random);
        var counts = new int[shares.size()];
        var left = shuffled.size();
        for (int i = 0; i < shares.size(); i++) {
            counts[i] = Math.clamp(shares.get(i).wanted(shuffled.size()), 0, Math.max(0, left - 1));
            left -= counts[i];
        }
        var roles = new ArrayList<List<UUID>>();
        var next = 0;
        for (int i = 0; i < shares.size(); i++) {
            var count = shares.get(i).isRest() ? left : counts[i];
            roles.add(List.copyOf(shuffled.subList(next, next + count)));
            next += count;
        }
        if (shares.stream().noneMatch(RoleShare::isRest)) {
            roles.add(List.copyOf(shuffled.subList(next, shuffled.size())));
        }
        return roles;
    }
}
