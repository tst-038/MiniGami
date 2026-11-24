package io.github.tst_038.minigami.koth.ledger;

import io.github.tst_038.minigami.core.storage.Storage;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Each player's spendable KOTH points, kept in storage. Every call runs on the storage thread, in the order
 * it was made, and each change is one statement, so points are never lost to two changes at once and a
 * balance never goes below 0. The futures complete on the storage thread.
 */
public final class PointsLedger {

    /** Resource folder of the schema migrations, unique so MiniGami's own migrations on the joined classpath never shadow them. */
    public static final String MIGRATIONS = "storage/koth/sqlite";

    private final Storage storage;

    public PointsLedger(Storage storage) {
        this.storage = storage;
    }

    /** @return the player's points, 0 if they never had any */
    public CompletableFuture<Long> balance(UUID player) {
        return storage.submit(connection -> {
            try (var statement = connection.prepareStatement("SELECT points FROM balances WHERE player = ?")) {
                statement.setString(1, player.toString());
                return orZero(statement);
            }
        });
    }

    /**
     * @param amount more than 0
     * @return the player's points now
     */
    public CompletableFuture<Long> add(UUID player, long amount) {
        requirePositive(amount);
        return storage.submit(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO balances (player, points) VALUES (?, ?) ON CONFLICT (player) DO UPDATE SET points = points + excluded.points RETURNING points")) {
                statement.setString(1, player.toString());
                statement.setLong(2, amount);
                return orZero(statement);
            }
        });
    }

    /**
     * Takes the points only if the player has all of them.
     *
     * @param amount more than 0
     * @return the player's points now, or empty if they had too few and nothing was taken
     */
    public CompletableFuture<OptionalLong> spend(UUID player, long amount) {
        requirePositive(amount);
        return storage.submit(connection -> {
            try (var statement = connection.prepareStatement("UPDATE balances SET points = points - ? WHERE player = ? AND points >= ? RETURNING points")) {
                statement.setLong(1, amount);
                statement.setString(2, player.toString());
                statement.setLong(3, amount);
                try (var result = statement.executeQuery()) {
                    return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
                }
            }
        });
    }

    /**
     * Takes up to that many points, for an admin: a player with fewer is left with 0.
     *
     * @param amount more than 0
     * @return the player's points now
     */
    public CompletableFuture<Long> take(UUID player, long amount) {
        requirePositive(amount);
        return storage.submit(connection -> {
            try (var statement = connection.prepareStatement("UPDATE balances SET points = MAX(points - ?, 0) WHERE player = ? RETURNING points")) {
                statement.setLong(1, amount);
                statement.setString(2, player.toString());
                return orZero(statement);
            }
        });
    }

    /** The first column of the first row the statement gives, 0 if it gives none. */
    private static long orZero(PreparedStatement statement) throws SQLException {
        try (var result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("The amount must be more than 0, not " + amount);
        }
    }
}
