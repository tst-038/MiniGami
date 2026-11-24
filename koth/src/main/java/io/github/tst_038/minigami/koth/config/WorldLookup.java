package io.github.tst_038.minigami.koth.config;

import java.util.Optional;
import org.bukkit.World;

/** Finds, and if need be loads, a world by name. Server thread only. */
@FunctionalInterface
public interface WorldLookup {

    /** The world, or empty when there is none of that name to load. */
    Optional<World> load(String name);
}
