package io.github.tst_038.minigami.paper.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A database in one SQLite file, through the driver the server ships. One connection, used only by
 * the storage thread, which opens the file and brings its schema up to date before any other work.
 * Written ahead to a log (WAL), so a crash loses at most the last transactions, never half of one.
 */
public final class SqliteStorage implements Storage {

    private final Logger logger;
    private final ExecutorService thread = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("MiniGami storage").factory());
    /** Only touched on the storage thread; null if the database couldn't be opened. */
    private Connection connection;

    /** Opens the file, made if missing, in the background; work submitted meanwhile waits for it. */
    public SqliteStorage(Path file, Migrations migrations, Logger logger) {
        this.logger = logger;
        thread.execute(() -> {
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                var opened = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                try (var statement = opened.createStatement()) {
                    statement.execute("PRAGMA journal_mode = WAL");
                    statement.execute("PRAGMA foreign_keys = ON");
                }
                opened.setAutoCommit(false);
                var version = migrations.apply(opened);
                connection = opened;
                logger.info("Storage opened: " + file.getFileName() + ", schema version " + version);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not open the storage " + file + "; nothing will be stored", e);
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> submit(Work<T> work) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                if (connection == null) {
                    throw new IllegalStateException("The storage is not open");
                }
                try {
                    var result = work.run(connection);
                    connection.commit();
                    return result;
                } catch (SQLException e) {
                    rollback();
                    throw new CompletionException(e);
                } catch (RuntimeException e) {
                    rollback();
                    throw e;
                }
            }, thread);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("The storage is closed", e));
        }
    }

    private void rollback() {
        try {
            connection.rollback();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not roll back a failed storage transaction", e);
        }
    }

    @Override
    public void close() {
        try {
            thread.execute(() -> {
                if (connection == null) {
                    return;
                }
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Could not close the storage", e);
                }
                connection = null;
            });
        } catch (RejectedExecutionException e) {
            return;
        }
        thread.shutdown();
        try {
            if (!thread.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.severe("The storage was still busy at shutdown; its last work may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
