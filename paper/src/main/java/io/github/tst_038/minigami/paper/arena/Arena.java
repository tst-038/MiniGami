package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.region.Region;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A place in a world that matches are played in: where players start, optionally where they wait before the
 * start, named regions that games use, in the order of the file, and the box its saved map covers. Locations are handed out as copies, since
 * Bukkit's are mutable.
 */
public record Arena(String id, World world, List<Spawn> spawns, Optional<Location> waitingSpot, Map<String, Region> regions,
        Optional<Region> map) {

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
        regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
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
