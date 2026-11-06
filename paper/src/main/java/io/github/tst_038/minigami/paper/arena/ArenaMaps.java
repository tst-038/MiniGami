package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Grid;
import io.github.tst_038.minigami.api.region.Region;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Arena maps saved as WorldEdit schematics in {@code arenas/<id>/map.schem}, pasted back into the
 * arena's map box to undo what a match changed, also when the server stopped mid-match. An arena takes no new match while its map
 * is being pasted. Without WorldEdit (or FastAsyncWorldEdit) or a saved map, nothing is pasted.
 *
 * <p>A region can have a schematic of its own in {@code arenas/<id>/regions/<name>.schem}, what a
 * game turns it into during a match, such as a wall that crumbles.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ArenaMaps {

    private final Plugin plugin;
    private final Messages messages;
    /** Only made when WorldEdit is there, since it loads WorldEdit's classes. */
    private final Optional<WorldEditMaps> worldEdit;
    private final Set<String> resetting = new HashSet<>();
    /** The last edit queued per arena, which the next one waits for so edits never overlap. */
    private final Map<String, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();

    /** @param blocksPerTick how many blocks a tick may place when there is no FastAsyncWorldEdit; read at every edit, so a reload applies */
    public ArenaMaps(Plugin plugin, Messages messages, IntSupplier blocksPerTick) {
        this.plugin = plugin;
        this.messages = messages;
        this.worldEdit = plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null
                ? Optional.empty()
                : Optional.of(new WorldEditMaps(plugin, plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null, blocksPerTick));
    }

    /** Whether WorldEdit or FastAsyncWorldEdit is there, which saving and pasting maps need. */
    public boolean hasWorldEdit() {
        return worldEdit.isPresent();
    }

    /** Whether the arena's map is being pasted, so it can't host a match yet. */
    public boolean isResetting(String arenaId) {
        return resetting.contains(arenaId);
    }

    /**
     * Saves the player's WorldEdit selection, which must be in the arena's world, as the arena's map.
     *
     * @param saved gets the box the selection covered, once the file is written, to keep as the arena's map box
     */
    void save(Player player, Arena arena, Consumer<Region> saved) {
        if (!canSelect(player, arena)) {
            return;
        }
        worldEdit.get().save(player, file(arena)).ifPresentOrElse(
                saving -> onServerThread(saving.written(), problem -> {
                    if (problem == null) {
                        saved.accept(saving.box());
                    } else {
                        plugin.getLogger().log(Level.WARNING, "Could not save the map of " + arena.id(), problem);
                        messages.send(player, "arena.map.save-failed", Placeholder.unparsed("arena", arena.id()));
                    }
                }),
                () -> messages.send(player, "arena.map.no-selection"));
    }

    /**
     * Saves the player's WorldEdit selection as what the arena's region turns into when a game
     * replaces it. It is pasted with its lowest corner on the region's, so it should be the region's size.
     */
    void saveRegionSchematic(Player player, Arena arena, String region) {
        if (!canSelect(player, arena)) {
            return;
        }
        worldEdit.get().save(player, regionFile(arena, region)).ifPresentOrElse(
                saving -> onServerThread(saving.written(), problem -> {
                    if (problem == null) {
                        messages.send(player, "arena.region.schematic-saved", Placeholder.unparsed("region", region));
                    } else {
                        plugin.getLogger().log(Level.WARNING, "Could not save the schematic of " + arena.id() + "'s region " + region, problem);
                        messages.send(player, "arena.map.save-failed", Placeholder.unparsed("arena", arena.id()));
                    }
                }),
                () -> messages.send(player, "arena.map.no-selection"));
    }

    /**
     * Replaces the region's blocks with its schematic, or with air if it has none, for a match. The
     * map reset after the match puts them back.
     *
     * @throws IllegalStateException without WorldEdit, which a game that changes blocks can't be played without
     */
    public void replace(Arena arena, Region region) {
        var maps = worldEdit.orElseThrow(() -> new IllegalStateException("Replacing a region needs WorldEdit"));
        onServerThread(queue(arena, () -> maps.replace(region, regionFile(arena, region.id()))), problem -> {
            if (problem != null) {
                plugin.getLogger().log(Level.WARNING, "Could not replace " + arena.id() + "'s region " + region.id(), problem);
            }
        });
    }

    /**
     * Fills boxes for a match, after every earlier edit of the arena and before the map reset after the
     * match. The map reset puts them back.
     *
     * @return completes on the server thread
     * @throws IllegalStateException without WorldEdit, which a game that changes blocks can't be played without
     */
    public CompletableFuture<Void> fill(Arena arena, List<Fill> fills) {
        var maps = worldEdit.orElseThrow(() -> new IllegalStateException("Filling blocks needs WorldEdit"));
        fills.stream().flatMap(fill -> Stream.concat(Stream.of(fill.material()), fill.mix().keySet().stream())).filter(material -> !material.isBlock()).findFirst().ifPresent(material -> {
            throw new IllegalArgumentException(material + " is not a block");
        });
        return queue(arena, () -> maps.fill(fills)).whenCompleteAsync((ignored, problem) -> {
            if (problem != null) {
                plugin.getLogger().log(Level.WARNING, "Could not fill blocks in " + arena.id(), problem);
            }
        }, serverThread());
    }

    /**
     * Puts grids into the arena for a match, after every earlier edit of the arena and before the map
     * reset after the match, which puts the old blocks back.
     *
     * @param cancelled asked between batches without FastAsyncWorldEdit; once true the rest isn't placed
     * @return completes on the server thread
     * @throws IllegalStateException without WorldEdit, which a game that changes blocks can't be played without
     */
    public CompletableFuture<Void> place(Arena arena, CompletableFuture<List<Grid>> grids, BooleanSupplier cancelled) {
        var maps = worldEdit.orElseThrow(() -> new IllegalStateException("Placing blocks needs WorldEdit"));
        return queue(arena, () -> grids.thenComposeAsync(ready -> maps.place(ready, cancelled), serverThread())).whenCompleteAsync((ignored, problem) -> {
            if (problem != null) {
                plugin.getLogger().log(Level.WARNING, "Could not place blocks in " + arena.id(), problem);
            }
        }, serverThread());
    }

    /**
     * The player's WorldEdit selection, which must be in the arena's world, as a region of that name.
     * Tells the player what is missing if there is none.
     */
    Optional<Region> selection(Player player, Arena arena, String name) {
        if (!canSelect(player, arena)) {
            return Optional.empty();
        }
        var selection = worldEdit.get().selection(player, name);
        if (selection.isEmpty()) {
            messages.send(player, "arena.region.no-selection");
        }
        return selection;
    }

    private boolean canSelect(Player player, Arena arena) {
        if (worldEdit.isEmpty()) {
            messages.send(player, "arena.map.no-worldedit");
            return false;
        }
        if (!arena.world().equals(player.getWorld())) {
            messages.send(player, "arena.other-world", Placeholder.unparsed("arena", arena.id()),
                    Placeholder.unparsed("world", arena.world().getName()));
            return false;
        }
        return true;
    }

    /**
     * Pastes the arena's saved map, if it has one.
     *
     * @param feedback hears how it went; empty after a match
     */
    public void reset(Arena arena, Optional<Audience> feedback) {
        if (!plugin.isEnabled()) {
            // Shutting down: the paste at the next startup takes care of it.
            return;
        }
        if (worldEdit.isEmpty()) {
            feedback.ifPresent(audience -> messages.send(audience, "arena.map.no-worldedit"));
            return;
        }
        if (!hasMap(arena)) {
            feedback.ifPresent(audience -> messages.send(audience, "arena.map.none", Placeholder.unparsed("arena", arena.id())));
            return;
        }
        if (!resetting.add(arena.id())) {
            feedback.ifPresent(audience -> messages.send(audience, "arena.map.resetting", Placeholder.unparsed("arena", arena.id())));
            return;
        }
        onServerThread(queue(arena, () -> worldEdit.get().paste(arena.map().orElseThrow(), file(arena))), problem -> {
            resetting.remove(arena.id());
            if (problem == null) {
                feedback.ifPresent(audience -> messages.send(audience, "arena.map.reset", Placeholder.unparsed("arena", arena.id())));
            } else {
                plugin.getLogger().log(Level.WARNING, "Could not reset the map of " + arena.id(), problem);
                feedback.ifPresent(audience -> messages.send(audience, "arena.map.reset-failed", Placeholder.unparsed("arena", arena.id())));
            }
        });
    }

    /** Pastes every saved map, to undo matches the server stopped in the middle of. */
    public void resetAll(Collection<Arena> arenas) {
        arenas.forEach(arena -> reset(arena, Optional.empty()));
    }

    /** Whether the arena has a map box and a saved map. Checks the disk on the server thread, but a single stat is too short to matter. */
    boolean hasMap(Arena arena) {
        return arena.map().isPresent() && Files.exists(file(arena));
    }

    private Path file(Arena arena) {
        return plugin.getDataPath().resolve("arenas").resolve(arena.id()).resolve("map.schem");
    }

    /** Puts the region's schematic on the player's WorldEdit clipboard. */
    void loadRegionSchematic(Player player, Arena arena, String region) {
        onServerThread(worldEdit.get().load(player, regionFile(arena, region)), problem -> {
            if (problem == null) {
                messages.send(player, "arena.region.schematic-loaded", Placeholder.unparsed("region", region));
            } else {
                plugin.getLogger().log(Level.WARNING, "Could not load the schematic of " + arena.id() + "'s region " + region, problem);
                messages.send(player, "arena.map.load-failed", Placeholder.unparsed("arena", arena.id()));
            }
        });
    }

    /** Whether the region has a saved schematic. Checks the disk on the server thread, but a single stat is too short to matter. */
    boolean hasRegionSchematic(Arena arena, String region) {
        return Files.exists(regionFile(arena, region));
    }

    private Path regionFile(Arena arena, String region) {
        return plugin.getDataPath().resolve("arenas").resolve(arena.id()).resolve("regions").resolve(region + ".schem");
    }

    /** Runs the work on the server thread once the arena's earlier edits are done, whether they worked or not. */
    private CompletableFuture<Void> queue(Arena arena, Supplier<CompletableFuture<Void>> work) {
        var earlier = pending.getOrDefault(arena.id(), CompletableFuture.completedFuture(null));
        var next = earlier.handle((ignored, problem) -> (Void) null).thenComposeAsync(ignored -> work.get(), serverThread());
        pending.put(arena.id(), next);
        next.whenComplete((ignored, problem) -> pending.remove(arena.id(), next));
        return next;
    }

    private Executor serverThread() {
        return task -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    /** @param done gets the failure, or {@code null} on success */
    private void onServerThread(CompletableFuture<Void> work, Consumer<Throwable> done) {
        work.whenComplete((ignored, problem) -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> done.accept(problem)));
    }
}
