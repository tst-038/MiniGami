package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.region.TeamRegion;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.game.TeamSetup;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * An arena's spawns, which are a list and so can't be changed with a plain {@code set}, its map, and
 * going there. Changes are saved through the {@link ConfigEditor}, so they are checked like any other edit.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ArenaSetup {

    private final Supplier<IdRegistry<Arena>> arenas;
    private final ConfigEditor editor;
    private final ArenaMaps maps;
    private final Predicate<String> inUse;
    private final Messages messages;
    private final Plugin plugin;

    /**
     * @param arenas the arenas currently loaded
     * @param inUse whether a match is running in the arena with this id
     */
    public ArenaSetup(Supplier<IdRegistry<Arena>> arenas, ConfigEditor editor, ArenaMaps maps, Predicate<String> inUse, Messages messages,
            Plugin plugin) {
        this.arenas = arenas;
        this.editor = editor;
        this.maps = maps;
        this.inUse = inUse;
        this.messages = messages;
        this.plugin = plugin;
    }

    /** Saves the player's WorldEdit selection as the map pasted back after each match. */
    public void saveMap(Player player, String id) {
        find(player, id).ifPresent(arena -> {
            if (inUse.test(id)) {
                messages.send(player, "arena.in-use", Placeholder.unparsed("arena", id));
                return;
            }
            maps.save(player, arena, box -> editor.change(player, Kind.ARENA, id, config -> ArenaFile.setMap(config, box),
                    "arena.map.saved", Placeholder.unparsed("arena", id)));
        });
    }

    /** Pastes the arena's saved map now, unless a match is running there. */
    public void resetMap(CommandSender sender, String id) {
        find(sender, id).ifPresent(arena -> {
            if (inUse.test(id)) {
                messages.send(sender, "arena.in-use", Placeholder.unparsed("arena", id));
                return;
            }
            maps.reset(arena, Optional.of(sender));
        });
    }

    /** Saves the player's WorldEdit selection as the region, replacing one of that name. */
    public void setRegion(Player player, String id, String name) {
        if (!IdRegistry.isValid(name) || name.equals(TeamRegion.KEYWORD)) {
            messages.send(player, "arena.region.invalid-name", Placeholder.unparsed("region", name));
            return;
        }
        find(player, id).flatMap(arena -> maps.selection(player, arena, name)).ifPresent(region ->
                editor.change(player, Kind.ARENA, id, config -> ArenaFile.setRegion(config, region), "arena.region.saved",
                        Placeholder.unparsed("arena", id), Placeholder.unparsed("region", name), corners(region)));
    }

    public void removeRegion(CommandSender sender, String id, String name) {
        region(sender, id, name).ifPresent(region -> editor.change(sender, Kind.ARENA, id, config -> ArenaFile.removeRegion(config, name),
                "arena.region.removed", Placeholder.unparsed("arena", id), Placeholder.unparsed("region", name)));
    }

    /** Saves the player's WorldEdit selection as what the region turns into when a game replaces it. */
    public void saveRegionSchematic(Player player, String id, String name) {
        region(player, id, name).ifPresent(region -> maps.saveRegionSchematic(player, arenas.get().find(id).orElseThrow(), name));
    }

    /** Loads the region into the player's WorldEdit selection, doing nothing else. */
    public void selectRegion(Player player, String id, String name) {
        region(player, id, name).ifPresent(region -> maps.select(player, arenas.get().find(id).orElseThrow(), region));
    }

    /** Loads the arena's saved map box into the player's WorldEdit selection. */
    public void selectMap(Player player, String id) {
        find(player, id).ifPresent(arena -> arena.map().ifPresentOrElse(map -> maps.select(player, arena, map),
                () -> messages.send(player, "arena.map.none", Placeholder.unparsed("arena", id))));
    }

    /** Puts the region's schematic on the player's WorldEdit clipboard. */
    public void loadRegionSchematic(Player player, String id, String name) {
        region(player, id, name).ifPresent(region -> {
            var arena = arenas.get().find(id).orElseThrow();
            if (!maps.hasWorldEdit()) {
                messages.send(player, "arena.map.no-worldedit");
            } else if (!maps.hasRegionSchematic(arena, name)) {
                messages.send(player, "arena.region.no-schematic", Placeholder.unparsed("region", name));
            } else {
                maps.loadRegionSchematic(player, arena, name);
            }
        });
    }

    /** Pastes the region's schematic into the world to look at it; the arena's map reset puts the old blocks back. */
    public void pasteRegionSchematic(CommandSender sender, String id, String name) {
        region(sender, id, name).ifPresent(region -> {
            var arena = arenas.get().find(id).orElseThrow();
            if (!maps.hasWorldEdit()) {
                messages.send(sender, "arena.map.no-worldedit");
            } else if (!maps.hasRegionSchematic(arena, name)) {
                messages.send(sender, "arena.region.no-schematic", Placeholder.unparsed("region", name));
            } else {
                maps.replace(arena, region);
                messages.send(sender, "arena.region.schematic-pasted", Placeholder.unparsed("region", name));
            }
        });
    }

    public void regions(CommandSender sender, String id) {
        find(sender, id).ifPresent(arena -> {
            if (arena.regions().isEmpty()) {
                messages.send(sender, "arena.region.none", Placeholder.unparsed("arena", id));
            }
            arena.regions().values().forEach(region -> messages.send(sender, "arena.info-region",
                    Placeholder.unparsed("region", region.id()), corners(region)));
            if (!arena.lootPoints().isEmpty()) {
                messages.send(sender, "arena.info-loot", Placeholder.unparsed("arena", id), Formatter.number("count", arena.lootPoints().size()));
            }
        });
    }

    /** The names of the arena's regions, for completion; none if there is no such arena. */
    public List<String> regionNames(String id) {
        return arenas.get().find(id).map(arena -> List.copyOf(arena.regions().keySet())).orElse(List.of());
    }

    /** Outlines the region's edges in particles, only for this player, for a few seconds. */
    public void showRegion(Player player, String id, String name) {
        region(player, id, name).ifPresent(region -> {
            messages.send(player, "arena.region.showing", Placeholder.unparsed("region", name), Formatter.number("seconds", SHOW_SECONDS));
            int[] left = {SHOW_SECONDS * 2};
            player.getScheduler().runAtFixedRate(plugin, task -> {
                if (--left[0] < 0 || !player.getWorld().equals(region.world())) {
                    task.cancel();
                    return;
                }
                outline(player, region);
            }, null, 1, 10);
        });
    }

    private static final int SHOW_SECONDS = 10;

    /** The twelve edges, along the outside of the blocks, spaced so a big region stays at about a thousand particles. */
    private static void outline(Player player, Region region) {
        double[] min = {region.minX(), region.minY(), region.minZ()};
        double[] max = {region.maxX() + 1, region.maxY() + 1, region.maxZ() + 1};
        var step = Math.max(1, 4 * (max[0] - min[0] + max[1] - min[1] + max[2] - min[2]) / 1000);
        for (int corner = 0; corner < 8; corner++) {
            for (int axis = 0; axis < 3; axis++) {
                if ((corner & 1 << axis) != 0) {
                    continue;
                }
                double[] at = new double[3];
                for (int i = 0; i < 3; i++) {
                    at[i] = (corner & 1 << i) == 0 ? min[i] : max[i];
                }
                for (var along = min[axis]; along <= max[axis]; along += step) {
                    at[axis] = along;
                    player.spawnParticle(Particle.END_ROD, at[0], at[1], at[2], 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private Optional<Region> region(Audience audience, String id, String name) {
        return find(audience, id).flatMap(arena -> {
            var region = Optional.ofNullable(arena.regions().get(name));
            if (region.isEmpty()) {
                messages.send(audience, "arena.region.unknown", Placeholder.unparsed("arena", id), Placeholder.unparsed("region", name));
            }
            return region;
        });
    }

    /**
     * Adds where the player stands, which must be in the arena's world.
     *
     * @param team the only team or role that starts there, or empty for everyone
     */
    public void addSpawn(Player player, String id, Optional<String> team) {
        if (team.isPresent() && !TeamSetup.IDS.contains(team.get()) && !IdRegistry.isValid(team.get())) {
            messages.send(player, "arena.unknown-team", Placeholder.unparsed("team", team.get()),
                    Placeholder.unparsed("teams", String.join(", ", TeamSetup.IDS)));
            return;
        }
        var here = player.getLocation();
        find(player, id).ifPresent(arena -> {
            if (!arena.world().equals(here.getWorld())) {
                messages.send(player, "arena.other-world", Placeholder.unparsed("arena", id),
                        Placeholder.unparsed("world", arena.world().getName()));
                return;
            }
            editor.change(player, Kind.ARENA, id, config -> ArenaFile.addSpawn(config, here, team), "arena.spawn-added",
                    Placeholder.unparsed("arena", id), Formatter.number("number", arena.spawns().size() + 1));
        });
    }

    /** @param number as shown by {@link #info}, starting at 1 */
    public void removeSpawn(CommandSender sender, String id, int number) {
        find(sender, id).ifPresent(arena -> {
            if (number > arena.spawns().size()) {
                messages.send(sender, "arena.no-spawn", Placeholder.unparsed("arena", id), Formatter.number("number", number));
                return;
            }
            editor.change(sender, Kind.ARENA, id, config -> ArenaFile.removeSpawn(config, number - 1), "arena.spawn-removed",
                    Placeholder.unparsed("arena", id), Formatter.number("number", number));
        });
    }

    /** Lists the arena's points; a player can click one to go there. */
    public void info(CommandSender sender, String id) {
        find(sender, id).ifPresent(arena -> {
            messages.send(sender, "arena.info", Placeholder.unparsed("arena", id),
                    Placeholder.unparsed("world", arena.world().getName()), Formatter.number("spawns", arena.spawns().size()));
            spawns(sender, arena);
            arena.waitingSpot().ifPresentOrElse(
                    spot -> messages.send(sender, "arena.info-waiting-spot", point(spot)),
                    () -> messages.send(sender, "arena.info-no-waiting-spot"));
            arena.spectatorSpot().ifPresentOrElse(
                    spot -> messages.send(sender, "arena.info-spectator-spot", point(spot)),
                    () -> messages.send(sender, "arena.info-no-spectator-spot"));
            arena.respawningSpot().ifPresentOrElse(
                    spot -> messages.send(sender, "arena.info-respawning-spot", point(spot)),
                    () -> messages.send(sender, "arena.info-no-respawning-spot"));
            arena.map().filter(box -> maps.hasMap(arena)).ifPresentOrElse(
                    box -> messages.send(sender, "arena.info-map", corners(box)),
                    () -> messages.send(sender, "arena.info-no-map", Placeholder.unparsed("arena", id)));
            arena.regions().values().forEach(region -> messages.send(sender, "arena.info-region",
                    Placeholder.unparsed("region", region.id()), corners(region)));
        });
    }

    /** Lists the arena's spawns by number, as {@link #removeSpawn} takes them. */
    public void spawns(CommandSender sender, String id) {
        find(sender, id).ifPresent(arena -> {
            if (arena.spawns().isEmpty()) {
                messages.send(sender, "arena.no-spawns", Placeholder.unparsed("arena", id));
            }
            spawns(sender, arena);
        });
    }

    private void spawns(CommandSender sender, Arena arena) {
        var spawns = arena.spawns();
        for (int i = 0; i < spawns.size(); i++) {
            var team = spawns.get(i).team()
                    .map(id -> messages.render("arena.spawn-team", Placeholder.component("team",
                            TeamSetup.IDS.contains(id) ? messages.render("team." + id) : Component.text(id))))
                    .orElse(Component.empty());
            messages.send(sender, "arena.info-spawn", point(spawns.get(i).location(), Formatter.number("number", i + 1),
                    Placeholder.component("team", team)));
        }
    }

    /** To the waiting spot, else the first spawn, else the spawn of the arena's world. */
    public void teleport(Player player, String id) {
        find(player, id).ifPresent(arena -> player.teleportAsync(arena.waitingSpot()
                .or(() -> arena.spawns().stream().findFirst().map(Arena.Spawn::location))
                .orElseGet(() -> arena.world().getSpawnLocation())));
    }

    private Optional<Arena> find(Audience audience, String id) {
        var arena = arenas.get().find(id);
        if (arena.isEmpty()) {
            messages.send(audience, "arena.unknown", Placeholder.unparsed("arena", id));
        }
        return arena;
    }

    private static TagResolver corners(Region box) {
        return TagResolver.resolver(Placeholder.unparsed("from", box.minX() + ", " + box.minY() + ", " + box.minZ()),
                Placeholder.unparsed("to", box.maxX() + ", " + box.maxY() + ", " + box.maxZ()));
    }

    private static TagResolver point(Location location, TagResolver... extra) {
        return TagResolver.resolver(TagResolver.resolver(extra),
                Placeholder.unparsed("x", "%.1f".formatted(location.getX())),
                Placeholder.unparsed("y", "%.1f".formatted(location.getY())),
                Placeholder.unparsed("z", "%.1f".formatted(location.getZ())),
                Placeholder.styling("teleport", ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker) {
                        clicker.teleportAsync(location);
                    }
                })));
    }
}
