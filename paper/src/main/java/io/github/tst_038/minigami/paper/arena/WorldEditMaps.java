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
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/**
 * Everything that touches WorldEdit's classes, so the plugin still loads without it. Blocks are
 * copied and pasted on the server thread; the file is read and written off it.
 */
final class WorldEditMaps {

    private final Executor offThread;
    private final Executor serverThread;

    WorldEditMaps(Plugin plugin) {
        this.offThread = task -> plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run());
        this.serverThread = task -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    /**
     * Copies the player's selection, entities included, and writes it to the file. Pasting puts it
     * back where it was copied from.
     *
     * @return empty if the player has no complete selection in their world
     */
    Optional<CompletableFuture<Void>> save(Player player, Path file) {
        var world = BukkitAdapter.adapt(player.getWorld());
        Region selection;
        try {
            selection = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(world);
        } catch (IncompleteRegionException e) {
            return Optional.empty();
        }
        var clipboard = new BlockArrayClipboard(selection);
        clipboard.setOrigin(selection.getMinimumPoint());
        try (var edit = WorldEdit.getInstance().newEditSession(world)) {
            var copy = new ForwardExtentCopy(edit, selection, clipboard, selection.getMinimumPoint());
            copy.setCopyingEntities(true);
            Operations.complete(copy);
        } catch (WorldEditException e) {
            return Optional.of(CompletableFuture.failedFuture(e));
        }
        return Optional.of(CompletableFuture.runAsync(() -> write(clipboard, file), offThread));
    }

    /** Reads the file and pastes it where it was copied from, after removing the entities there, players aside. */
    CompletableFuture<Void> paste(World world, Path file) {
        return CompletableFuture.supplyAsync(() -> read(file), offThread)
                .thenAcceptAsync(clipboard -> paste(world, clipboard), serverThread);
    }

    private static void paste(World world, Clipboard clipboard) {
        var min = clipboard.getRegion().getMinimumPoint();
        var max = clipboard.getRegion().getMaximumPoint();
        world.getNearbyEntities(new BoundingBox(min.x(), min.y(), min.z(), max.x() + 1, max.y() + 1, max.z() + 1),
                entity -> !(entity instanceof Player)).forEach(Entity::remove);
        try (var edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operations.complete(new ClipboardHolder(clipboard).createPaste(edit)
                    .to(clipboard.getOrigin())
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
