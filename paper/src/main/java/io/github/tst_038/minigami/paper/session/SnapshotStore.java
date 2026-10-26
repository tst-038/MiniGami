package io.github.tst_038.minigami.paper.session;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Snapshots on disk, one file per player, so they survive a restart or crash. A restored snapshot
 * is kept as the player's last one, so staff can still look at it or put it back. All file access
 * runs on one background thread, in the order it was asked for, so a load never sees a save or
 * retire that was asked for earlier as not yet done.
 */
public final class SnapshotStore implements AutoCloseable {

    private final Path directory;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("MiniGami snapshots").factory());

    public SnapshotStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    /** Writes the snapshot still to be restored, in the background. The text is made here, on the calling thread. */
    public void save(UUID id, PlayerSnapshot snapshot) {
        save(file(id), snapshot);
    }

    /** Replaces the player's last snapshot, in the background. */
    public void saveLast(UUID id, PlayerSnapshot snapshot) {
        save(lastFile(id), snapshot);
    }

    /** Makes the snapshot still to be restored the player's last one, replacing the previous last one. */
    public void retire(UUID id) {
        io.execute(() -> {
            try {
                Files.move(file(id), lastFile(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (NoSuchFileException e) {
                // Nothing was saved, so there is nothing to keep.
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not retire the snapshot of " + id, e);
            }
        });
    }

    /**
     * The snapshot still to be restored. Blocks until the file is read, so never call it from the server thread.
     *
     * @throws IllegalStateException if the file exists but can't be read
     */
    public Optional<PlayerSnapshot> load(UUID id) {
        return load(file(id));
    }

    /**
     * The snapshot that was restored last. Blocks like {@link #load}.
     *
     * @throws IllegalStateException if the file exists but can't be read
     */
    public Optional<PlayerSnapshot> loadLast(UUID id) {
        return load(lastFile(id));
    }

    private Path file(UUID id) {
        return directory.resolve(id + ".yml");
    }

    private Path lastFile(UUID id) {
        return directory.resolve(id + ".last.yml");
    }

    private void save(Path file, PlayerSnapshot snapshot) {
        var bytes = snapshot.toYaml().getBytes(StandardCharsets.UTF_8);
        io.execute(() -> {
            try {
                write(file, bytes);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not save " + file, e);
            }
        });
    }

    private Optional<PlayerSnapshot> load(Path file) {
        var text = CompletableFuture.supplyAsync(() -> {
            try {
                return Optional.of(Files.readString(file));
            } catch (NoSuchFileException e) {
                return Optional.<String>empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, io);
        try {
            return text.join().map(PlayerSnapshot::fromYaml);
        } catch (CompletionException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not read " + file, e instanceof CompletionException ? e.getCause() : e);
        }
    }

    /** Finishes every pending write and delete. */
    @Override
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.severe("Snapshot files were still being written at shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Write to a temporary file, flush it to the disk, then swap it in: a crash leaves either the old or the new file, never half of one.
    private void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        var temporary = directory.resolve(file.getFileName() + ".tmp");
        try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
