package io.github.tst_038.minigami.paper.arena;

import java.util.List;
import java.util.Optional;
import org.bukkit.Location;

/**
 * A place matches are played in: where players start and, optionally, where they wait before the
 * start. Locations are handed out as copies, since Bukkit's are mutable.
 */
public record Arena(String id, List<Location> spawns, Optional<Location> waitingSpot) {

    public Arena {
        spawns = spawns.stream().map(Location::clone).toList();
        waitingSpot = waitingSpot.map(Location::clone);
    }

    @Override
    public List<Location> spawns() {
        return spawns.stream().map(Location::clone).toList();
    }

    @Override
    public Optional<Location> waitingSpot() {
        return waitingSpot.map(Location::clone);
    }

    /** An arena is set up step by step in-game; it can only host matches once it has a spawn. */
    public boolean isPlayable() {
        return !spawns.isEmpty();
    }
}
