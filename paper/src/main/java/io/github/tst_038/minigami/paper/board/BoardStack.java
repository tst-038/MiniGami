package io.github.tst_038.minigami.paper.board;

import io.github.tst_038.minigami.api.board.BoardPriority;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Which board each player sees: the claim of highest priority, of equal ones the latest. It says through {@code show} whenever
 * that changes, also back to the original once the last claim is gone. Knows nothing of the server, so it is plain bookkeeping.
 *
 * <p>Not thread-safe: use it from the server thread only.
 *
 * @param <K> what identifies a player
 * @param <B> a board
 */
final class BoardStack<K, B> {

    /** One claim; open until released or dropped. */
    static final class Claim<K, B> {
        private final K key;
        private final Object owner;
        private final B board;
        private final BoardPriority priority;
        private boolean open = true;

        private Claim(K key, Object owner, B board, BoardPriority priority) {
            this.key = key;
            this.owner = owner;
            this.board = board;
            this.priority = priority;
        }
    }

    private record Held<K, B>(B original, List<Claim<K, B>> claims) {
    }

    private final Map<K, Held<K, B>> players = new HashMap<>();
    private final BiConsumer<K, B> show;

    BoardStack(BiConsumer<K, B> show) {
        this.show = show;
    }

    /** @param current the board the player has now; it is the one to give back if this is their first claim */
    Claim<K, B> add(K key, B current, Object owner, B board, BoardPriority priority) {
        var held = players.computeIfAbsent(key, k -> new Held<>(current, new ArrayList<>()));
        var before = top(held);
        var claim = new Claim<>(key, owner, board, priority);
        held.claims.add(claim);
        if (top(held) != before) {
            show.accept(key, top(held).board);
        }
        return claim;
    }

    /** Gives the claim up. Does nothing if it is already released or its player was dropped. */
    void release(Claim<K, B> claim) {
        if (!claim.open) {
            return;
        }
        claim.open = false;
        var held = players.get(claim.key);
        var before = top(held);
        held.claims.remove(claim);
        if (held.claims.isEmpty()) {
            players.remove(claim.key);
            show.accept(claim.key, held.original);
        } else if (top(held) != before) {
            show.accept(claim.key, top(held).board);
        }
    }

    /** Forgets the player and closes their claims without showing anything, for a player who is gone. */
    void drop(K key) {
        var held = players.remove(key);
        if (held != null) {
            held.claims.forEach(claim -> claim.open = false);
        }
    }

    /** The open claims of the owner. */
    List<Claim<K, B>> ownedBy(Object owner) {
        return players.values().stream().flatMap(held -> held.claims.stream()).filter(claim -> Objects.equals(claim.owner, owner)).toList();
    }

    private static <K, B> Claim<K, B> top(Held<K, B> held) {
        Claim<K, B> best = null;
        for (var claim : held.claims) {
            if (best == null || claim.priority.ordinal() <= best.priority.ordinal()) {
                best = claim;
            }
        }
        return best;
    }
}
