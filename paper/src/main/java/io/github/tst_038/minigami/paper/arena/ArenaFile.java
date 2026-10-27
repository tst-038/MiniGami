package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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

    /** @param team the only team that starts there, or empty for everyone */
    public static void addSpawn(YamlConfiguration config, Location location, Optional<String> team) {
        var spawns = new ArrayList<Map<?, ?>>(config.getMapList("spawns"));
        var spawn = ConfigLocations.write(location);
        team.ifPresent(id -> spawn.put("team", id));
        spawns.add(spawn);
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

    /** The box the saved map is pasted back into. */
    public static void setMap(YamlConfiguration config, Region box) {
        var map = new LinkedHashMap<String, Object>();
        map.put("from", corner(box.minX(), box.minY(), box.minZ()));
        map.put("to", corner(box.maxX(), box.maxY(), box.maxZ()));
        config.set("map", map);
    }

    private static Map<String, Integer> corner(int x, int y, int z) {
        var corner = new LinkedHashMap<String, Integer>();
        corner.put("x", x);
        corner.put("y", y);
        corner.put("z", z);
        return corner;
    }
}
