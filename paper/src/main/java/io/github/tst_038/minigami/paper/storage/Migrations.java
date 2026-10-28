package io.github.tst_038.minigami.paper.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Brings a database's schema up to date from numbered SQL files bundled with the plugin:
 * {@code 1.sql}, {@code 2.sql} and so on, each run once, in order, in a transaction of its own. The
 * database remembers in {@code schema_version} which it has. A file is never changed once released;
 * a change to the schema is a new file.
 */
public final class Migrations {

    private final ClassLoader resources;
    private final String folder;

    /** @param folder the resource folder with the files, such as {@code storage/sqlite} */
    public Migrations(ClassLoader resources, String folder) {
        this.resources = resources;
        this.folder = folder;
    }

    /**
     * Runs the files the database doesn't have yet. Blocking IO.
     *
     * @return the schema version the database is at now
     * @throws SQLException if a file fails; the database stays at the version before it
     */
    int apply(Connection connection) throws SQLException {
        var version = version(connection);
        for (Optional<String> next; (next = file(version + 1)).isPresent(); version++) {
            try (var statement = connection.createStatement()) {
                for (var sql : statements(next.get())) {
                    statement.execute(sql);
                }
                statement.executeUpdate("UPDATE schema_version SET version = " + (version + 1));
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new SQLException("Migration " + folder + "/" + (version + 1) + ".sql failed", e);
            }
        }
        return version;
    }

    private static int version(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            int version;
            try (var result = statement.executeQuery("SELECT version FROM schema_version")) {
                version = result.next() ? result.getInt(1) : -1;
            }
            if (version < 0) {
                statement.executeUpdate("INSERT INTO schema_version (version) VALUES (0)");
                version = 0;
            }
            connection.commit();
            return version;
        }
    }

    private Optional<String> file(int version) {
        try (var in = resources.getResourceAsStream(folder + "/" + version + ".sql")) {
            return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // shortcut: splits on ';' at the end of a line, so a statement that holds one there (a trigger body) can't be written yet.
    static List<String> statements(String file) {
        var withoutComments = String.join("\n", file.lines().filter(line -> !line.strip().startsWith("--")).toList());
        return Arrays.stream(withoutComments.split(";\\s*(\\n|$)")).map(String::strip).filter(sql -> !sql.isEmpty()).toList();
    }
}
