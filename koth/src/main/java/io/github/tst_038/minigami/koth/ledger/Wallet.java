package io.github.tst_038.minigami.koth.ledger;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link PointsLedger} and the {@link Balances} shown from it, kept in step. Every result comes back on the
 * server thread, where the cache is updated before anything else sees it. Storage keeps the order of the
 * changes and the server thread keeps the order of the results, so the cache ends at the latest balance.
 */
public final class Wallet {

    private final PointsLedger ledger;
    private final Balances balances;
    private final Executor serverThread;
    private final Logger logger;

    public Wallet(PointsLedger ledger, Balances balances, Executor serverThread, Logger logger) {
        this.ledger = ledger;
        this.balances = balances;
        this.serverThread = serverThread;
        this.logger = logger;
    }

    public Balances balances() {
        return balances;
    }

    /** Reads the player's points into the cache, for when they join. A failure is logged. */
    public void load(UUID player) {
        logged(balance(player), "load the points of " + player);
    }

    /** Gives the player points. A failure is logged, as nobody waits for it. */
    public void credit(UUID player, long amount) {
        logged(refreshed(ledger.add(player, amount), player), "give " + amount + " points to " + player);
    }

    /** @return the player's points now, or empty if they had too few and nothing was taken */
    public CompletableFuture<OptionalLong> spend(UUID player, long amount) {
        return ledger.spend(player, amount).thenApplyAsync(left -> {
            left.ifPresent(points -> balances.put(player, points));
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

    /** Drops the player from the cache, for when they leave. */
    public void forget(UUID player) {
        balances.forget(player);
    }

    private CompletableFuture<Long> refreshed(CompletableFuture<Long> points, UUID player) {
        return points.thenApplyAsync(now -> {
            balances.put(player, now);
            return now;
        }, serverThread);
    }

    private void logged(CompletableFuture<?> work, String what) {
        work.exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not " + what, failure);
            return null;
        });
    }
}
