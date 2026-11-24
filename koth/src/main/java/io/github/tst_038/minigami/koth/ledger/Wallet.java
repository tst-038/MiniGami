package io.github.tst_038.minigami.koth.ledger;

import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link PointsLedger} and the {@link Balances} shown from it, kept in step. Every result comes back on the
 * server thread, where the cache is updated before anything else sees it. Storage keeps the order of the
 * changes and the server thread keeps the order of the results, so the cache ends at the latest balance.
 * The cache only holds players who are {@link #load loaded}: a result that arrives after {@link #forget} (or
 * for someone who never was loaded, such as an offline player staff gave points to) is not cached.
 */
public final class Wallet {

    private final PointsLedger ledger;
    private final Balances balances;
    private final Executor serverThread;
    private final Logger logger;
    /** Who the cache is kept for. */
    private final Set<UUID> present = ConcurrentHashMap.newKeySet();
    /** Whether the last write that failed has not been followed by one that worked, so a broken database is logged once. */
    private final AtomicBoolean failing = new AtomicBoolean();

    public Wallet(PointsLedger ledger, Balances balances, Executor serverThread, Logger logger) {
        this.ledger = ledger;
        this.balances = balances;
        this.serverThread = serverThread;
        this.logger = logger;
    }

    public Balances balances() {
        return balances;
    }

    /** Starts keeping the player's points in the cache and reads them, for when they join. A failure is logged. */
    public void load(UUID player) {
        present.add(player);
        logged(ledger.balance(player), player, "load the points of " + player);
    }

    /** Gives the player points. A failure is logged, as nobody waits for it. */
    public void credit(UUID player, long amount) {
        logged(ledger.add(player, amount), player, "give " + amount + " points to " + player);
    }

    /** @return the player's points now, after giving them that many */
    public CompletableFuture<Long> give(UUID player, long amount) {
        return refreshed(ledger.add(player, amount), player);
    }

    /** @return the player's points now, or empty if they had too few and nothing was taken */
    public CompletableFuture<OptionalLong> spend(UUID player, long amount) {
        return ledger.spend(player, amount).thenApplyAsync(left -> {
            left.ifPresent(points -> cache(player, points));
            return left;
        }, serverThread);
    }

    /** @return the player's points now, after taking up to that many */
    public CompletableFuture<Long> take(UUID player, long amount) {
        return refreshed(ledger.take(player, amount), player);
    }

    /** @return the player's points, read from storage */
    public CompletableFuture<Long> balance(UUID player) {
        return refreshed(ledger.balance(player), player);
    }

    /** Stops keeping the player's points in the cache and drops them, for when they leave. */
    public void forget(UUID player) {
        present.remove(player);
        balances.forget(player);
    }

    private CompletableFuture<Long> refreshed(CompletableFuture<Long> points, UUID player) {
        return points.thenApplyAsync(now -> {
            cache(player, now);
            return now;
        }, serverThread);
    }

    /** On the server thread, with the result of a write that worked. */
    private void cache(UUID player, long points) {
        failing.set(false);
        if (present.contains(player)) {
            balances.put(player, points);
        }
    }

    /**
     * Logs what goes wrong, telling a failed write from a failed hop back to the server thread, which is all that
     * is left to go wrong once the write worked, as when the plugin is switching off. A database that never opened
     * fails every second, so it is logged on the first failure and again only after a write has worked.
     */
    private void logged(CompletableFuture<Long> stored, UUID player, String what) {
        refreshed(stored, player).exceptionally(failure -> {
            if (!stored.isCompletedExceptionally()) {
                logger.log(Level.SEVERE, "The storage worked to " + what + ", but the points shown could not be updated, as the server thread is gone", failure);
            } else if (failing.compareAndSet(false, true)) {
                logger.log(Level.SEVERE, "Could not " + what + ". Until a write works again, further failures are not logged.", failure);
            }
            return null;
        });
    }
}
