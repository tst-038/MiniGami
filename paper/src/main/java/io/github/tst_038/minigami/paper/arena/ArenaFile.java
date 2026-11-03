package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.loot.LootPoint;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.paper.config.ConfigLocations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/** The changes the arena commands make to an {@code arenas/<id>/arena.yml}, keeping the rest of the file as it is. */
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

    /** Adds the point, or replaces the one at its position in place: a container has one table. */
    public static void setLootPoint(YamlConfiguration config, LootPoint point) {
        var points = new ArrayList<Map<?, ?>>(config.getMapList("loot"));
        var item = point.toConfig();
        for (int i = 0; i < points.size(); i++) {
            if (sameBlock(points.get(i), point)) {
                points.set(i, item);
                config.set("loot", points);
                return;
            }
        }
        points.add(item);
        config.set("loot", points);
    }

    /** @return {@code false} if there is no loot point at the position */
    public static boolean removeLootPoint(YamlConfiguration config, int x, int y, int z) {
        var points = new ArrayList<Map<?, ?>>(config.getMapList("loot"));
        if (!points.removeIf(point -> sameBlock(point, x, y, z))) {
            return false;
        }
        config.set("loot", points.isEmpty() ? null : points);
        return true;
    }

    private static boolean sameBlock(Map<?, ?> item, LootPoint point) {
        return sameBlock(item, point.x(), point.y(), point.z());
    }

    private static boolean sameBlock(Map<?, ?> item, int x, int y, int z) {
        return Integer.valueOf(x).equals(item.get("x")) && Integer.valueOf(y).equals(item.get("y")) && Integer.valueOf(z).equals(item.get("z"));
    }

    /** The box the saved map is pasted back into. */
    public static void setMap(YamlConfiguration config, Region box) {
        config.set("map", box(box));
    }

    /** Adds the region, or replaces the one with its name in place. */
    public static void setRegion(YamlConfiguration config, Region region) {
        config.set("regions." + region.id(), box(region));
    }

    /** @return {@code false} if there is no region with that name */
    public static boolean removeRegion(YamlConfiguration config, String name) {
        if (!config.contains("regions." + name)) {
            return false;
        }
        config.set("regions." + name, null);
        if (config.getConfigurationSection("regions").getKeys(false).isEmpty()) {
            config.set("regions", null);
        }
        return true;
    }

    private static Map<String, Object> box(Region region) {
        var box = new LinkedHashMap<String, Object>();
        box.put("from", corner(region.minX(), region.minY(), region.minZ()));
        box.put("to", corner(region.maxX(), region.maxY(), region.maxZ()));
        return box;
    }

    private static Map<String, Integer> corner(int x, int y, int z) {
        var corner = new LinkedHashMap<String, Integer>();
        corner.put("x", x);
        corner.put("y", y);
        corner.put("z", z);
        return corner;
    }
}
