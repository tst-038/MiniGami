package io.github.tst_038.minigami.core.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Splits players at random into teams of even size, except that each group, such as a party, stays
 * together. Groups go first, the biggest first, each whole into the currently smallest team; everyone
 * else is then dealt one by one into the smallest team, a team without a group first. So a group only
 * gets players it doesn't know when the teams can't be made even otherwise. With no groups that is a
 * plain random split.
 *
 * <p>Whether a group fits in a team is up to the caller. A group bigger than a team is still kept
 * together, and the teams are then uneven.
 */
public final class GroupedTeams implements TeamFormation {

    private final Supplier<? extends Collection<? extends Collection<UUID>>> groups;
    private final RandomGenerator random;

    /** @param groups asked at each {@link #form}, so it can follow groups that change; members who don't play are ignored */
    public GroupedTeams(Supplier<? extends Collection<? extends Collection<UUID>>> groups, RandomGenerator random) {
        this.groups = groups;
        this.random = random;
    }

    @Override
    public List<List<UUID>> form(List<UUID> players, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Needs at least one team, got " + count);
        }
        var playing = new HashSet<>(players);
        var placed = new HashSet<UUID>();
        var together = new ArrayList<List<UUID>>();
        for (var group : groups.get()) {
            // A player in two groups stays with the first.
            var present = group.stream().filter(member -> playing.contains(member) && placed.add(member)).toList();
            if (!present.isEmpty()) {
                together.add(present);
            }
        }
        // Shuffled first so groups of the same size are not always placed in the same order.
        Collections.shuffle(together, random);
        together.sort(Comparator.comparingInt((List<UUID> group) -> group.size()).reversed());
        var alone = new ArrayList<>(players.stream().filter(player -> !placed.contains(player)).toList());
        Collections.shuffle(alone, random);

        var teams = new ArrayList<List<UUID>>();
        for (int team = 0; team < count; team++) {
            teams.add(new ArrayList<>());
        }
        var grouped = new ArrayList<List<UUID>>();
        together.forEach(group -> {
            var team = smallest(teams, List.of());
            team.addAll(group);
            grouped.add(team);
        });
        alone.forEach(player -> smallest(teams, grouped).add(player));
        return teams.stream().map(List::copyOf).toList();
    }

    /**
     * One of the smallest teams, picked at random so the first team is not always the one with the biggest group.
     *
     * @param avoid teams to pick only when no other team is as small
     */
    private List<UUID> smallest(List<List<UUID>> teams, List<List<UUID>> avoid) {
        var size = teams.stream().mapToInt(List::size).min().orElseThrow();
        var smallest = teams.stream().filter(team -> team.size() == size).toList();
        // Compared by identity: two empty teams are equal lists.
        var preferred = smallest.stream().filter(team -> avoid.stream().noneMatch(other -> other == team)).toList();
        var pick = preferred.isEmpty() ? smallest : preferred;
        return pick.get(random.nextInt(pick.size()));
    }
}
