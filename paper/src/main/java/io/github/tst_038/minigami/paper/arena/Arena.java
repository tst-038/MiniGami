package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.feature.ArenaLayout;
import io.github.tst_038.minigami.api.loot.LootPoint;
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
 * start, named regions that games use, in the order of the file, the containers matches fill with loot, and the box its saved map covers. Locations are handed out as copies, since
 * Bukkit's are mutable.
 *
 * <p>{@code playRegion} is the region a game plays in when it picked one (see {@link #within}).
 */
public record Arena(String id, World world, List<Spawn> spawns, Optional<Location> waitingSpot, Optional<Location> spectatorSpot,
        Map<String, Region> regions, Optional<Region> map, List<LootPoint> lootPoints, Optional<Region> playRegion, Optional<Location> respawningSpot) implements ArenaLayout {

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
        spectatorSpot = spectatorSpot.map(Location::clone);
        respawningSpot = respawningSpot.map(Location::clone);
        regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
        lootPoints = List.copyOf(lootPoints);
    }

    /** An arena that isn't narrowed to a region. */
    public Arena(String id, World world, List<Spawn> spawns, Optional<Location> waitingSpot, Optional<Location> spectatorSpot,
            Map<String, Region> regions, Optional<Region> map, List<LootPoint> lootPoints) {
        this(id, world, spawns, waitingSpot, spectatorSpot, regions, map, lootPoints, Optional.empty(), Optional.empty());
    }

    /** An arena without loot points. */
    public Arena(String id, World world, List<Spawn> spawns, Optional<Location> waitingSpot, Optional<Location> spectatorSpot,
            Map<String, Region> regions, Optional<Region> map) {
        this(id, world, spawns, waitingSpot, spectatorSpot, regions, map, List.of());
    }

    @Override
    public Optional<Location> waitingSpot() {
        return waitingSpot.map(Location::clone);
    }

    /** Where players who are out watch from; without it they spectate where they fell. */
    @Override
    public Optional<Location> spectatorSpot() {
        return spectatorSpot.map(Location::clone);
    }

    /** Where players wait for a delayed respawn; without it they spectate at the spawn they will get. */
    public Optional<Location> respawningSpot() {
        return respawningSpot.map(Location::clone);
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

    /** This arena for a game that plays in one of its regions: only the spawns inside it, or standing on its top layer, are left. Map, regions and loot stay whole. */
    public Arena within(Region region) {
        return new Arena(id, world, spawns.stream().filter(spawn -> region.containsStanding(spawn.location())).toList(), waitingSpot, spectatorSpot, regions, map,
                lootPoints, Optional.of(region), respawningSpot);
    }

    @Override
    public List<Region> regions(String group) {
        return regions.values().stream().filter(region -> region.isIn(group)).toList();
    }

    @Override
    public List<Location> spawnPoints() {
        return locations(spawn -> true);
    }

    private List<Location> locations(Predicate<Spawn> filter) {
        return spawns.stream().filter(filter).map(Spawn::location).toList();
    }

    /** An arena is set up step by step in-game; it can only host matches once it has a spawn. */
    public boolean isPlayable() {
        return !spawns.isEmpty();
    }
}
