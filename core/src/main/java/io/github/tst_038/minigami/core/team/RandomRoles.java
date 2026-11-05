package io.github.tst_038.minigami.core.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Shuffles the players and gives each role its share, in order, keeping groups such as parties on one role. The rest role gets whoever is left,
 * and always at least one player when there is anyone, so a role that asks for too many gets fewer.
 */
public final class RandomRoles {

    private final RandomGenerator random;

    public RandomRoles(RandomGenerator random) {
        this.random = random;
    }

    /** Deals the roles with every player on their own. */
    public List<List<UUID>> deal(List<UUID> players, List<RoleShare> shares) {
        return deal(players, shares, List.of());
    }

    /**
     * Deals the roles so that each group, such as a party, stays on one role. A group only takes a role's share if it fits
     * what is still open, so a role may get fewer players than it asks for. A role that no group fits gets the smallest
     * group, or, when that is all that is left, players one by one, so a role is never left empty by groups alone.
     *
     * @param groups players who go together; members who don't play are ignored, and a player in two groups stays with the first
     * @return the players of each role, in the order of {@code shares}; without a rest share one more, last list holds the
     *         players left over (at least one when there is anyone)
     * @throws IllegalArgumentException if more than one share is the rest
     */
    public List<List<UUID>> deal(List<UUID> players, List<RoleShare> shares, Collection<? extends Collection<UUID>> groups) {
        if (shares.stream().filter(RoleShare::isRest).count() > 1) {
            throw new IllegalArgumentException("Needs at most one rest role, got " + shares);
        }
        var units = units(players, groups);
        Collections.shuffle(units, random);
        var counts = new int[shares.size()];
        var left = players.size();
        for (int i = 0; i < shares.size(); i++) {
            counts[i] = Math.clamp(shares.get(i).wanted(players.size()), 0, Math.max(0, left - 1));
            left -= counts[i];
        }
        var roles = new ArrayList<List<UUID>>();
        for (int i = 0; i < shares.size(); i++) {
            roles.add(shares.get(i).isRest() ? List.of() : take(units, counts[i]));
        }
        var rest = units.stream().flatMap(List::stream).toList();
        for (int i = 0; i < shares.size(); i++) {
            if (shares.get(i).isRest()) {
                roles.set(i, rest);
            }
        }
        if (shares.stream().noneMatch(RoleShare::isRest)) {
            roles.add(rest);
        }
        return roles.stream().map(List::copyOf).toList();
    }

    /** Each group of players who play, then every other player alone. */
    private static List<List<UUID>> units(List<UUID> players, Collection<? extends Collection<UUID>> groups) {
        var playing = new HashSet<>(players);
        var placed = new HashSet<UUID>();
        var units = new ArrayList<List<UUID>>();
        for (var group : groups) {
            var present = group.stream().filter(member -> playing.contains(member) && placed.add(member)).toList();
            if (!present.isEmpty()) {
                units.add(new ArrayList<>(present));
            }
        }
        players.stream().filter(placed::add).forEach(player -> units.add(new ArrayList<>(List.of(player))));
        return units;
    }

    /** Removes from {@code units}, in order, the units that fit in {@code count} players, and returns their players. */
    private static List<UUID> take(List<List<UUID>> units, int count) {
        var taken = new ArrayList<UUID>();
        for (var unit = units.iterator(); unit.hasNext() && taken.size() < count; ) {
            var next = unit.next();
            if (next.size() <= count - taken.size()) {
                taken.addAll(next);
                unit.remove();
            }
        }
        if (taken.isEmpty() && count > 0) {
            var smallest = units.stream().min(Comparator.comparingInt(List::size)).orElseThrow();
            if (units.size() > 1) {
                taken.addAll(smallest);
                units.remove(smallest);
            } else {
                // shortcut: one group is all that is left, so it is split rather than leave the role empty; keep it whole if a game must
                taken.addAll(smallest.subList(0, count));
                smallest.subList(0, count).clear();
            }
        }
        return taken;
    }
}
