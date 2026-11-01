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
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Region;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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

    /** shortcut: fixed budget per tick, make it a setting if a real server lags. */
    static final int BLOCKS_PER_TICK = 4096;

    private final Plugin plugin;
    private final boolean fawe;
    private final Executor offThread;
    private final Executor serverThread;

    WorldEditMaps(Plugin plugin, boolean fawe) {
        this.plugin = plugin;
        this.fawe = fawe;
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

    /**
     * Pastes the file as {@link #paste(Region, Path)} does if it exists, else fills the box with air.
     */
    CompletableFuture<Void> replace(Region box, Path file) {
        return CompletableFuture.supplyAsync(() -> Files.exists(file) ? Optional.of(read(file)) : Optional.<Clipboard>empty(), offThread)
                .thenAcceptAsync(clipboard -> clipboard.ifPresentOrElse(found -> paste(box, found), () -> clear(box)), serverThread);
    }

    /**
     * Fills the boxes in order: as one edit off the server thread with FastAsyncWorldEdit, else in
     * batches of {@link #BLOCKS_PER_TICK} blocks, one per tick, on the server thread.
     */
    CompletableFuture<Void> fill(List<Fill> fills) {
        if (fawe) {
            return CompletableFuture.runAsync(() -> apply(fills), offThread);
        }
        var batches = FillBatches.of(fills, BLOCKS_PER_TICK);
        var done = new CompletableFuture<Void>();
        if (batches.isEmpty()) {
            done.complete(null);
        } else {
            serverThread.execute(() -> runBatch(batches, 0, done));
        }
        return done;
    }

    private void runBatch(List<List<Fill>> batches, int index, CompletableFuture<Void> done) {
        try {
            apply(batches.get(index));
        } catch (RuntimeException e) {
            done.completeExceptionally(e);
            return;
        }
        if (index + 1 == batches.size()) {
            done.complete(null);
        } else {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> runBatch(batches, index + 1, done), 1);
        }
    }

    private static void apply(List<Fill> fills) {
        if (fills.isEmpty()) {
            return;
        }
        var world = BukkitAdapter.adapt(fills.getFirst().box().world());
        try (var edit = WorldEdit.getInstance().newEditSession(world)) {
            for (var fill : fills) {
                var box = fill.box();
                edit.setBlocks(new CuboidRegion(world, BlockVector3.at(box.minX(), box.minY(), box.minZ()),
                        BlockVector3.at(box.maxX(), box.maxY(), box.maxZ())), pattern(fill));
            }
        } catch (WorldEditException e) {
            throw new CompletionException(e);
        }
    }

    private static Pattern pattern(Fill fill) {
        if (fill.mix().isEmpty()) {
            return BukkitAdapter.asBlockType(fill.material()).getDefaultState();
        }
        var random = new RandomPattern();
        fill.mix().forEach((material, weight) -> random.add(BukkitAdapter.asBlockType(material).getDefaultState(), weight));
        return random;
    }

    private static void clear(Region box) {
        var world = BukkitAdapter.adapt(box.world());
        try (var edit = WorldEdit.getInstance().newEditSession(world)) {
            edit.setBlocks(new CuboidRegion(world, BlockVector3.at(box.minX(), box.minY(), box.minZ()),
                    BlockVector3.at(box.maxX(), box.maxY(), box.maxZ())), BlockTypes.AIR.getDefaultState());
        } catch (WorldEditException e) {
            throw new CompletionException(e);
        }
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
