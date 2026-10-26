package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.paper.config.ConfigLocations;
import java.util.ArrayList;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/** The changes the arena commands make to an {@code arenas/<id>.yml}, keeping the rest of the file as it is. */
public final class ArenaFile {

    private ArenaFile() {
    }

    /** Fills an empty file for a new arena without spawns. */
    public static void create(YamlConfiguration config, World world) {
        config.set("world", world.getName());
        config.set("spawns", new ArrayList<>());
    }

    public static void addSpawn(YamlConfiguration config, Location location) {
        var spawns = new ArrayList<Map<?, ?>>(config.getMapList("spawns"));
        spawns.add(ConfigLocations.write(location));
        config.set("spawns", spawns);
    }

    /** @return {@code false} if there is no spawn at that index */
    public static boolean removeSpawn(YamlConfiguration config, int index) {
        var spawns = new ArrayList<Map<?, ?>>(config.getMapList("spawns"));
        if (index < 0 || index >= spawns.size()) {
            return false;
        }
        spawns.remove(index);
        config.set("spawns", spawns);
        return true;
    }

    public static void setWaitingSpot(YamlConfiguration config, Location location) {
        config.set("waiting-spot", ConfigLocations.write(location));
    }
}
