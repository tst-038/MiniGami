package io.github.tst_038.minigami.core.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * An SQL database an application keeps its data in. All work runs on one storage thread, never the
 * caller's own thread, one piece after the other in the order it was submitted. Write SQL every
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
     * before it. Comes back on that thread, so hop back to the thread that owns the state before touching it.
     *
     * @return what the work returned, or its failure (an {@link SQLException} as the cause); the transaction is then rolled back
     */
    <T> CompletableFuture<T> submit(Work<T> work);

    /** Finishes all the work submitted so far, then closes the database; work submitted after fails. */
    @Override
    void close();
}
