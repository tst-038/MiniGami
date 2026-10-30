package io.github.tst_038.minigami.core.presence;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Who is held by the engine. Each thing that holds a player (their saved state, a place in a match
 * they wait in without any state saved) takes a {@link Claim}; the player counts as present from
 * their first claim to their last release, so overlapping claims, such as a match inside a game
 * night, enter and leave once. Server thread only.
 */
public final class Presence {

    /** One hold on a player. Closing it more than once does nothing. */
    public interface Claim extends AutoCloseable {
        @Override
        void close();
    }

    private final Map<UUID, Integer> claims = new HashMap<>();
    private final Consumer<UUID> entered;
    private final Consumer<UUID> left;

    /**
     * @param entered called after the player became present, so {@link #contains} is true
     * @param left called after the player stopped being present, so {@link #contains} is false
     */
    public Presence(Consumer<UUID> entered, Consumer<UUID> left) {
        this.entered = entered;
        this.left = left;
    }

    public boolean contains(UUID id) {
        return claims.containsKey(id);
    }

    public Claim claim(UUID id) {
        var first = claims.merge(id, 1, Integer::sum) == 1;
        if (first) {
            entered.accept(id);
        }
        return new Claim() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                if (claims.merge(id, -1, Integer::sum) == 0) {
                    claims.remove(id);
                    left.accept(id);
                }
            }
        };
    }
}
