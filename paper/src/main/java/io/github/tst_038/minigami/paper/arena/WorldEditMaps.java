package io.github.tst_038.minigami.paper.arena;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import io.github.tst_038.minigami.api.region.Region;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/**
 * Everything that touches WorldEdit's classes, so the plugin still loads without it. Blocks are
 * copied and pasted on the server thread; the file is read and written off it.
 *
 * <p>Where a map goes is kept by the caller, not taken from the file: FastAsyncWorldEdit reads a
 * schematic whose origin is its corner as if it lay at 0, 0, 0.
 */
final class WorldEditMaps {

    private final Executor offThread;
    private final Executor serverThread;

    WorldEditMaps(Plugin plugin) {
        this.offThread = task -> plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run());
        this.serverThread = task -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    /**
     * @param box the selection that was copied
     * @param written completes once the file is written
     */
    record Saving(Region box, CompletableFuture<Void> written) {
    }

    /**
     * Copies the player's selection, entities included, and writes it to the file.
     *
     * @return empty if the player has no complete selection in their world
     */
    Optional<Saving> save(Player player, Path file) {
        var world = BukkitAdapter.adapt(player.getWorld());
        var found = selection(player);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var selection = found.get();
        var min = selection.getMinimumPoint();
        var box = box("map", player, selection);
        var clipboard = new BlockArrayClipboard(selection);
        clipboard.setOrigin(min);
        try (var edit = WorldEdit.getInstance().newEditSession(world)) {
            var copy = new ForwardExtentCopy(edit, selection, clipboard, min);
            copy.setCopyingEntities(true);
            Operations.complete(copy);
        } catch (WorldEditException e) {
            return Optional.of(new Saving(box, CompletableFuture.failedFuture(e)));
        }
        return Optional.of(new Saving(box, CompletableFuture.runAsync(() -> write(clipboard, file), offThread)));
    }

    /**
     * The box around the player's selection, named {@code id}.
     *
     * @return empty if the player has no complete selection in their world
     */
    Optional<Region> selection(Player player, String id) {
        return selection(player).map(selection -> box(id, player, selection));
    }

    private static Optional<com.sk89q.worldedit.regions.Region> selection(Player player) {
        try {
            return Optional.of(WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                    .getSelection(BukkitAdapter.adapt(player.getWorld())));
        } catch (IncompleteRegionException e) {
            return Optional.empty();
        }
    }

    private static Region box(String id, Player player, com.sk89q.worldedit.regions.Region selection) {
        var min = selection.getMinimumPoint();
        var max = selection.getMaximumPoint();
        return Region.between(id, player.getWorld(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /**
     * Reads the file and pastes it with its lowest corner on the box's, after removing the entities
     * in the box, players aside.
     */
    CompletableFuture<Void> paste(Region box, Path file) {
        return CompletableFuture.supplyAsync(() -> read(file), offThread)
                .thenAcceptAsync(clipboard -> paste(box, clipboard), serverThread);
    }

    private static void paste(Region box, Clipboard clipboard) {
        var world = box.world();
        world.getNearbyEntities(new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1),
                entity -> !(entity instanceof Player)).forEach(Entity::remove);
        // A block at p in the clipboard lands at to + (p - origin), so this puts its lowest corner on the box's.
        var to = BlockVector3.at(box.minX(), box.minY(), box.minZ())
                .add(clipboard.getOrigin().subtract(clipboard.getRegion().getMinimumPoint()));
        try (var edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operations.complete(new ClipboardHolder(clipboard).createPaste(edit)
                    .to(to)
                    .copyEntities(true)
                    .build());
        } catch (WorldEditException e) {
            throw new CompletionException(e);
        }
    }

    /** Writes next to the file first, so a crash midway leaves the old map. */
    private static void write(Clipboard clipboard, Path file) {
        var unfinished = file.resolveSibling(file.getFileName() + ".part");
        try {
            Files.createDirectories(file.getParent());
            try (var writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(Files.newOutputStream(unfinished))) {
                writer.write(clipboard);
            }
            Files.move(unfinished, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // findByPath is newer than FastAsyncWorldEdit's copy of this class.
    @SuppressWarnings("deprecation")
    private static Clipboard read(Path file) {
        var format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) {
            throw new UncheckedIOException(new IOException(file + " is not a schematic"));
        }
        try (var reader = format.getReader(Files.newInputStream(file))) {
            return reader.read();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
