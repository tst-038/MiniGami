package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.region.Region;
import java.util.List;
import org.bukkit.Location;

/** What a feature may look at of an arena when it checks that the arena suits it. */
public interface ArenaLayout {

    /** The arena's regions of a group, such as every {@code quarter}; see {@link Region#isIn}. */
    List<Region> regions(String group);

    /** Where players can start, whichever team. */
    List<Location> spawnPoints();
}
