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
 * Snapshots on disk, one file per player, so they survive a restart or crash. All file access runs
 * on one background thread, in the order it was asked for, so a load never sees a save or delete
 * that was asked for earlier as not yet done.
 */
public final class SnapshotStore implements AutoCloseable {

    private final Path directory;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("MiniGami snapshots").factory());

    public SnapshotStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    /** Writes in the background. The text is made here, on the calling thread. */
    public void save(UUID id, PlayerSnapshot snapshot) {
        var bytes = snapshot.toYaml().getBytes(StandardCharsets.UTF_8);
        io.execute(() -> {
            try {
                write(id, bytes);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not save the snapshot of " + id, e);
            }
        });
    }

    public void delete(UUID id) {
        io.execute(() -> {
            try {
                Files.deleteIfExists(file(id));
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not delete the snapshot of " + id, e);
            }
        });
    }

    /**
     * Blocks until the file is read, so never call it from the server thread.
     *
     * @throws IllegalStateException if the file exists but can't be read
     */
    public Optional<PlayerSnapshot> load(UUID id) {
        var text = CompletableFuture.supplyAsync(() -> {
            try {
                return Optional.of(Files.readString(file(id)));
            } catch (NoSuchFileException e) {
                return Optional.<String>empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, io);
        try {
            return text.join().map(PlayerSnapshot::fromYaml);
        } catch (CompletionException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not read the snapshot of " + id, e instanceof CompletionException ? e.getCause() : e);
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
    private void write(UUID id, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        var temporary = directory.resolve(id + ".yml.tmp");
        try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        Files.move(temporary, file(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path file(UUID id) {
        return directory.resolve(id + ".yml");
    }
}
