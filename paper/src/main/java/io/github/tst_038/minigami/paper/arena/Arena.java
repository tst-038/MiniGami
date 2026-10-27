package io.github.tst_038.minigami.paper.arena;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A place in a world that matches are played in: where players start and, optionally, where they wait before the
 * start. Locations are handed out as copies, since Bukkit's are mutable.
 */
public record Arena(String id, World world, List<Spawn> spawns, Optional<Location> waitingSpot) {

    /** @param team the team that starts here, or empty for a spawn anyone may use */
    public record Spawn(Location location, Optional<String> team) {

        public Spawn {
            location = location.clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    public Arena {
        spawns = List.copyOf(spawns);
        waitingSpot = waitingSpot.map(Location::clone);
    }

    @Override
    public Optional<Location> waitingSpot() {
        return waitingSpot.map(Location::clone);
    }

    /**
     * Where a player of this team starts: the team's own spawns, else the spawns without a team, else
     * every spawn. Without a team, the spawns without a team, else every spawn.
     */
    public List<Location> spawnsFor(Optional<String> team) {
        var own = team.map(id -> locations(spawn -> spawn.team().equals(team))).orElse(List.of());
        if (!own.isEmpty()) {
            return own;
        }
        var shared = locations(spawn -> spawn.team().isEmpty());
        return shared.isEmpty() ? locations(spawn -> true) : shared;
    }

    private List<Location> locations(Predicate<Spawn> filter) {
        return spawns.stream().filter(filter).map(Spawn::location).toList();
    }

    /** An arena is set up step by step in-game; it can only host matches once it has a spawn. */
    public boolean isPlayable() {
        return !spawns.isEmpty();
    }
}
