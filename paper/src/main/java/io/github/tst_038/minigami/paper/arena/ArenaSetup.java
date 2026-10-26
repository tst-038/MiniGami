package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Sets arenas up in-game: points are taken from where the admin stands. Changes are written to the
 * arena's file off the server thread, after which the config is loaded again.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ArenaSetup {

    /** Taken by the {@code /mg arena create} command, so an arena with this id couldn't be edited. */
    private static final String RESERVED_ID = "create";

    private final Plugin plugin;
    private final Supplier<IdRegistry<Arena>> arenas;
    private final Predicate<String> inUse;
    private final Messages messages;
    private final Runnable reload;

    /**
     * @param arenas the arenas currently loaded
     * @param inUse whether a running match is using the arena with this id
     * @param reload loads the config again, off the server thread
     */
    public ArenaSetup(Plugin plugin, Supplier<IdRegistry<Arena>> arenas, Predicate<String> inUse, Messages messages,
            Runnable reload) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.inUse = inUse;
        this.messages = messages;
        this.reload = reload;
    }

    public SortedSet<String> ids() {
        return arenas.get().ids();
    }

    /** A new arena without spawns in the admin's world. */
    public void create(Player player, String id) {
        var arena = Placeholder.unparsed("arena", id);
        if (!IdRegistry.isValid(id) || id.equals(RESERVED_ID)) {
            messages.send(player, "arena.invalid-id", arena);
            return;
        }
        if (arenas.get().find(id).isPresent()) {
            messages.send(player, "arena.exists", arena);
            return;
        }
        var world = player.getWorld();
        write(player, id, true, config -> ArenaFile.create(config, world), "arena.created", arena,
                Placeholder.unparsed("world", player.getWorld().getName()));
    }

    public void addSpawn(Player player, String id) {
        var here = player.getLocation();
        editable(player, id).ifPresent(arena -> write(player, id, false, config -> ArenaFile.addSpawn(config, here),
                "arena.spawn-added", Placeholder.unparsed("arena", id), Formatter.number("number", arena.spawns().size() + 1)));
    }

    /** @param number as shown by {@link #info}, starting at 1 */
    public void removeSpawn(Player player, String id, int number) {
        editable(player, id).ifPresent(arena -> {
            if (number > arena.spawns().size()) {
                messages.send(player, "arena.no-spawn", Placeholder.unparsed("arena", id), Formatter.number("number", number));
                return;
            }
            write(player, id, false, config -> ArenaFile.removeSpawn(config, number - 1),
                    "arena.spawn-removed", Placeholder.unparsed("arena", id), Formatter.number("number", number));
        });
    }

    public void setWaitingSpot(Player player, String id) {
        var here = player.getLocation();
        editable(player, id).ifPresent(arena -> write(player, id, false, config -> ArenaFile.setWaitingSpot(config, here),
                "arena.waiting-spot-set", Placeholder.unparsed("arena", id)));
    }

    /** Lists the arena's points; clicking one teleports there. */
    public void info(Player player, String id) {
        var found = arenas.get().find(id);
        if (found.isEmpty()) {
            messages.send(player, "arena.unknown", Placeholder.unparsed("arena", id));
            return;
        }
        var arena = found.get();
        messages.send(player, "arena.info", Placeholder.unparsed("arena", id),
                Placeholder.unparsed("world", arena.world().getName()), Formatter.number("spawns", arena.spawns().size()));
        var spawns = arena.spawns();
        for (int i = 0; i < spawns.size(); i++) {
            messages.send(player, "arena.info-spawn", point(spawns.get(i), Formatter.number("number", i + 1)));
        }
        arena.waitingSpot().ifPresentOrElse(
                spot -> messages.send(player, "arena.info-waiting-spot", point(spot)),
                () -> messages.send(player, "arena.info-no-waiting-spot"));
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

    /** The arena, if it exists, no match is using it, and the admin stands in its world. */
    private Optional<Arena> editable(Player player, String id) {
        var arena = arenas.get().find(id);
        if (arena.isEmpty()) {
            messages.send(player, "arena.unknown", Placeholder.unparsed("arena", id));
        } else if (inUse.test(id)) {
            messages.send(player, "arena.in-use", Placeholder.unparsed("arena", id));
        } else if (!arena.get().world().equals(player.getWorld())) {
            messages.send(player, "arena.other-world", Placeholder.unparsed("arena", id),
                    Placeholder.unparsed("world", arena.get().world().getName()));
        } else {
            return arena;
        }
        return Optional.empty();
    }

    /**
     * Applies the change to the arena's file off the server thread, then reports and reloads.
     * Synchronized so quick edits in a row don't overwrite each other.
     */
    private void write(Player player, String id, boolean create, Consumer<YamlConfiguration> change, String done,
            TagResolver... placeholders) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            synchronized (this) {
                var path = plugin.getDataPath().resolve("arenas").resolve(id + ".yml");
                try {
                    var config = new YamlConfiguration();
                    if (create) {
                        // A file that failed to load has no arena, but must not be overwritten.
                        if (Files.exists(path)) {
                            messages.send(player, "arena.exists", Placeholder.unparsed("arena", id));
                            return;
                        }
                        Files.createDirectories(path.getParent());
                    } else {
                        config.loadFromString(Files.readString(path));
                    }
                    change.accept(config);
                    // Through a temp file, so a crash mid-write never leaves a half-written arena.
                    var temp = path.resolveSibling(id + ".yml.tmp");
                    Files.writeString(temp, config.saveToString());
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException | InvalidConfigurationException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not write " + path, e);
                    messages.send(player, "arena.save-failed", Placeholder.unparsed("arena", id));
                    return;
                }
            }
            messages.send(player, done, placeholders);
            reload.run();
        });
    }
}
