package io.github.tst_038.minigami.gamenight.team;

import io.github.tst_038.minigami.core.team.GroupedTeams;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** The teams of a team night, formed once when the first game starts and kept after. Ids are {@code 1}, {@code 2}, … and the host can move players between them. */
public final class NightTeams {

    private final Map<String, Set<UUID>> teams = new LinkedHashMap<>();

    private NightTeams() {
    }

    /**
     * Forms {@code ceil(players / size)} teams with the players dealt out evenly, each party kept
     * whole in one team. The host moves players after this with {@link #move}.
     *
     * <p>A party bigger than {@code size} is not split but makes its team bigger, so the caller must
     * refuse such a party when it joins the night.
     *
     * @param players the night's players, in the order they joined
     * @param parties who play together; members who are not in the night are ignored
     */
    public static NightTeams form(List<UUID> players, List<Set<UUID>> parties, int size, RandomGenerator random) {
        var formed = new NightTeams();
        var count = Math.max(1, Math.ceilDiv(players.size(), size));
        var dealt = new GroupedTeams(() -> parties, random).form(players, count);
        for (var team : dealt) {
            formed.teams.put(formed.freeId(), new LinkedHashSet<>(team));
        }
        return formed;
    }

    public List<String> ids() {
        return List.copyOf(teams.keySet());
    }

    public Set<UUID> members(String id) {
        return Set.copyOf(teams.getOrDefault(id, Set.of()));
    }

    public Optional<String> teamOf(UUID player) {
        return teams.entrySet().stream().filter(team -> team.getValue().contains(player)).map(Map.Entry::getKey).findFirst();
    }

    /** For a late joiner; the smallest team may then be bigger than the night's team size. */
    public String addToSmallest(UUID player) {
        var smallest = teams.entrySet().stream().min(Comparator.comparingInt(team -> team.getValue().size()));
        var id = smallest.map(Map.Entry::getKey).orElseGet(() -> {
            var fresh = freeId();
            teams.put(fresh, new LinkedHashSet<>());
            return fresh;
        });
        teams.get(id).add(player);
        return id;
    }

    /**
     * Puts the player in the team, out of any other. A team left empty stays, so its id and its points are not reused.
     *
     * @throws IllegalArgumentException if there is no such team
     */
    public void move(UUID player, String id) {
        if (!teams.containsKey(id)) {
            throw new IllegalArgumentException("No night team " + id);
        }
        teams.values().forEach(team -> team.remove(player));
        teams.get(id).add(player);
    }

    private String freeId() {
        int number = 1;
        while (teams.containsKey(String.valueOf(number))) {
            number++;
        }
        return String.valueOf(number);
    }
}
