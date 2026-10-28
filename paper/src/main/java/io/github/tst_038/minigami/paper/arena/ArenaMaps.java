package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Region;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Arena maps saved as WorldEdit schematics in {@code arenas/<id>/map.schem}, pasted back into the
 * arena's map box to undo what a match changed, also when the server stopped mid-match. An arena takes no new match while its map
 * is being pasted. Without WorldEdit (or FastAsyncWorldEdit) or a saved map, nothing is pasted.
 *
 * <p>Call the public methods from the server thread.
 */
public final class ArenaMaps {

    private final Plugin plugin;
    private final Messages messages;
    /** Only made when WorldEdit is there, since it loads WorldEdit's classes. */
    private final Optional<WorldEditMaps> worldEdit;
    private final Set<String> resetting = new HashSet<>();

    public ArenaMaps(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.worldEdit = plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null
                ? Optional.empty()
                : Optional.of(new WorldEditMaps(plugin));
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
        onServerThread(worldEdit.get().paste(arena.map().orElseThrow(), file(arena)), problem -> {
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

    /** @param done gets the failure, or {@code null} on success */
    private void onServerThread(CompletableFuture<Void> work, Consumer<Throwable> done) {
        work.whenComplete((ignored, problem) -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> done.accept(problem)));
    }
}
