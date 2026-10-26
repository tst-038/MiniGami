package io.github.tst_038.minigami.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Owns resources and releases all of them when closed, newest first.
 *
 * <p>A child scope closes together with its parent but can also be closed on its own earlier.
 * Not thread-safe: use it from the server thread only.
 */
public final class Scope implements AutoCloseable {

    private final Scope parent;
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean closed;

    public Scope() {
        this(null);
    }

    private Scope(Scope parent) {
        this.parent = parent;
    }

    /**
     * Adds a resource to be closed with this scope.
     *
     * @throws IllegalStateException if this scope is already closed; the resource is closed immediately so it can't leak
     */
    public <T extends AutoCloseable> T register(T resource) {
        if (closed) {
            closeQuietly(resource);
            throw new IllegalStateException("Cannot register on a closed scope");
        }
        resources.push(resource);
        return resource;
    }

    public Scope child() {
        if (closed) {
            throw new IllegalStateException("Cannot create a child of a closed scope");
        }
        return register(new Scope(this));
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Closes every resource, newest first. A failing resource doesn't stop the others; failures are
     * rethrown afterwards as one {@link IllegalStateException}. Closing again does nothing.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (parent != null) {
            parent.resources.remove(this);
        }

        IllegalStateException failure = null;
        while (!resources.isEmpty()) {
            try {
                resources.pop().close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = new IllegalStateException("Failed to close a resource", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception ignored) {
            // The caller gets an IllegalStateException for the real mistake: registering too late.
        }
    }
}
