package io.github.tst_038.minigami.paper.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * The SQL database the plugin keeps its data in. All work runs on one storage thread, never the
 * server thread, one piece after the other in the order it was submitted. Write SQL every
 * implementation understands.
 */
public interface Storage extends AutoCloseable {

    /** Work on the database, given the connection to do it with; it all commits or none of it does. */
    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /**
     * Runs the work in a transaction of its own on the storage thread, after everything submitted
     * before it. Comes back on that thread, so hop to the server thread before touching the game.
     *
     * @return what the work returned, or its failure (an {@link SQLException} as the cause); the transaction is then rolled back
     */
    <T> CompletableFuture<T> submit(Work<T> work);

    /** Finishes all the work submitted so far, then closes the database; work submitted after fails. */
    @Override
    void close();
}
