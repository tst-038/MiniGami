package io.github.tst_038.minigami.gamenight.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** The teams of a team night, formed once when the first game starts and kept after. Ids are {@code 1}, {@code 2}, … unless the host named them. */
public final class NightTeams {

    private final Map<String, Set<UUID>> teams = new LinkedHashMap<>();

    private NightTeams() {
    }

    /**
     * Host assignments first, then each party together (split in teams of {@code size} if bigger),
     * then everyone else dealt at random into the teams with room, opening new ones as needed.
     *
     * @param players the night's players, in the order they joined
     * @param parties who play together; members who are not in the night are ignored
     * @param assigned a team id per player, wherever the host put them
     */
    public static NightTeams form(List<UUID> players, List<Set<UUID>> parties, Map<UUID, String> assigned, int size, RandomGenerator random) {
        var formed = new NightTeams();
        var rest = new ArrayList<>(players);
        for (UUID player : players) {
            if (assigned.containsKey(player)) {
                formed.teams.computeIfAbsent(assigned.get(player), id -> new LinkedHashSet<>()).add(player);
                rest.remove(player);
            }
        }
        for (Set<UUID> party : parties) {
            var members = rest.stream().filter(party::contains).toList();
            for (int from = 0; from < members.size(); from += size) {
                var team = new LinkedHashSet<>(members.subList(from, Math.min(from + size, members.size())));
                formed.teams.put(formed.freeId(), team);
                rest.removeAll(team);
            }
        }
        Collections.shuffle(rest, random);
        for (UUID player : rest) {
            var open = formed.teams.values().stream().filter(team -> team.size() < size).min(Comparator.comparingInt(Set::size));
            open.orElseGet(() -> formed.open()).add(player);
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

    private Set<UUID> open() {
        var team = new LinkedHashSet<UUID>();
        teams.put(freeId(), team);
        return team;
    }

    private String freeId() {
        int number = 1;
        while (teams.containsKey(String.valueOf(number))) {
            number++;
        }
        return String.valueOf(number);
    }
}
