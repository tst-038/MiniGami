package io.github.tst_038.minigami.core.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Shuffles the players and deals them out one by one, so team sizes differ by at most one. */
public final class RandomTeams implements TeamFormation {

    private final RandomGenerator random;

    public RandomTeams(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public List<List<UUID>> form(List<UUID> players, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Needs at least one team, got " + count);
        }
        var shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, random);
        var teams = new ArrayList<List<UUID>>();
        for (int team = 0; team < count; team++) {
            teams.add(new ArrayList<>());
        }
        for (int i = 0; i < shuffled.size(); i++) {
            teams.get(i % count).add(shuffled.get(i));
        }
        return teams.stream().map(List::copyOf).toList();
    }
}
