package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.koth.hill.Scoring;
import io.github.tst_038.minigami.koth.schedule.Plan;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Everything {@code config.yml} sets, read and checked.
 *
 * @param world the KOTH world
 * @param spawn where players are put down, if the config says; otherwise the world's spawn
 * @param respawnHere whether players who die in the world come back to the spawn
 */
public record KothConfig(World world, Optional<Location> spawn, boolean respawnHere, Plan plan, PointSettings point,
        Scoring scoring, SidebarLines sidebar) {

    public Location spawnOrDefault() {
        return spawn.orElseGet(world::getSpawnLocation);
    }
}
