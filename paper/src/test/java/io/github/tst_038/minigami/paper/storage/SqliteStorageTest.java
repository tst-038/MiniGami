package io.github.tst_038.minigami.paper.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStorageTest {

    private static final Logger LOGGER = Logger.getLogger(SqliteStorageTest.class.getName());

    @TempDir
    private Path folder;
    private final List<Storage> opened = new ArrayList<>();

    private Storage open(String migrations) {
        var storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/" + migrations), LOGGER);
        opened.add(storage);
        return storage;
    }

    @AfterEach
    void close() {
        opened.forEach(Storage::close);
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT version FROM schema_version")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static List<String> tables(Connection connection) throws SQLException {
        var tables = new ArrayList<String>();
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        return tables;
    }

    @Test
    void theSchemaIsBroughtUpToDateBeforeAnyWork() {
        var storage = open("test-migrations");

        assertEquals(2, storage.submit(SqliteStorageTest::schemaVersion).join());
        storage.submit(connection -> {
            try (var statement = connection.createStatement()) {
                return statement.executeUpdate("INSERT INTO players (id, name) VALUES ('alice', 'Alice')");
            }
        }).join();
    }

    @Test
    void reopeningRunsNoMigrationTwiceAndKeepsTheData() {
        var first = open("test-migrations");
        first.submit(connection -> {
            try (var statement = connection.createStatement()) {
                return statement.executeUpdate("INSERT INTO players (id) VALUES ('alice')");
            }
        }).join();
        first.close();

        var second = open("test-migrations");

        assertEquals(2, second.submit(SqliteStorageTest::schemaVersion).join());
        assertEquals(1, second.submit(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM players")) {
                result.next();
                return result.getInt(1);
            }
        }).join());
    }

    @Test
    void aFailedMigrationLeavesTheSchemaAtTheVersionBefore() {
        var storage = open("broken-migrations");
        var failed = assertThrows(CompletionException.class, () -> storage.submit(SqliteStorageTest::schemaVersion).join());
        assertInstanceOf(IllegalStateException.class, failed.getCause());
        storage.close();

        var check = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/none"), LOGGER);
        opened.add(check);

        assertEquals(1, check.submit(SqliteStorageTest::schemaVersion).join());
        assertEquals(List.of("fine", "schema_version"), check.submit(SqliteStorageTest::tables).join());
    }

    @Test
    void failedWorkIsRolledBack() {
        var storage = open("test-migrations");

        var failed = assertThrows(CompletionException.class, () -> storage.submit(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO players (id) VALUES ('alice')");
                // No such player, which the foreign key refuses.
                return statement.executeUpdate("INSERT INTO scores (player, points) VALUES ('bob', 3)");
            }
        }).join());

        assertInstanceOf(SQLException.class, failed.getCause());
        assertEquals(0, storage.submit(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM players")) {
                result.next();
                return result.getInt(1);
            }
        }).join());
    }

    @Test
    void workRunsInOrderOffTheCallingThreadAndCloseWaitsForIt() {
        var storage = open("test-migrations");
        var caller = Thread.currentThread();
        var order = Collections.synchronizedList(new ArrayList<Integer>());
        var offThread = new boolean[] {true};

        for (int i = 0; i < 50; i++) {
            var n = i;
            storage.submit(connection -> {
                offThread[0] &= Thread.currentThread() != caller;
                return order.add(n);
            });
        }
        storage.close();

        assertEquals(50, order.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(i, order.get(i));
        }
        assertTrue(offThread[0]);
    }

    @Test
    void workAfterCloseFails() {
        var storage = open("test-migrations");
        storage.close();

        var late = storage.submit(connection -> 1);

        assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class, late::join).getCause());
    }

    @Test
    void statementsAreSplitAtTheEndOfALineAndCommentsDropped() {
        assertEquals(List.of("CREATE TABLE a (x TEXT DEFAULT 'a;b')", "CREATE TABLE b (y INTEGER)"),
                Migrations.statements("-- two tables\nCREATE TABLE a (x TEXT DEFAULT 'a;b');\n\nCREATE TABLE b (y INTEGER);"));
    }
}
