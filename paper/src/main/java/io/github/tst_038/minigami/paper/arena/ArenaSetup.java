package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.mode.TeamSetup;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

    /**
     * @param arenas the arenas currently loaded
     * @param inUse whether a match is running in the arena with this id
     */
    public ArenaSetup(Supplier<IdRegistry<Arena>> arenas, ConfigEditor editor, ArenaMaps maps, Predicate<String> inUse, Messages messages) {
        this.arenas = arenas;
        this.editor = editor;
        this.maps = maps;
        this.inUse = inUse;
        this.messages = messages;
    }

    /** Saves the player's WorldEdit selection as the map pasted back after each match. */
    public void saveMap(Player player, String id) {
        find(player, id).ifPresent(arena -> maps.save(player, arena));
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

    /**
     * Adds where the player stands, which must be in the arena's world.
     *
     * @param team the only team that starts there, or empty for everyone
     */
    public void addSpawn(Player player, String id, Optional<String> team) {
        if (team.isPresent() && !TeamSetup.IDS.contains(team.get())) {
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
            messages.send(sender, maps.hasMap(arena) ? "arena.info-map" : "arena.info-no-map", Placeholder.unparsed("arena", id));
            arena.regions().values().forEach(region -> messages.send(sender, "arena.info-region",
                    Placeholder.unparsed("region", region.id()),
                    Placeholder.unparsed("from", region.minX() + ", " + region.minY() + ", " + region.minZ()),
                    Placeholder.unparsed("to", region.maxX() + ", " + region.maxY() + ", " + region.maxZ())));
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
                    .map(id -> messages.render("arena.spawn-team", Placeholder.component("team", messages.render("team." + id))))
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
