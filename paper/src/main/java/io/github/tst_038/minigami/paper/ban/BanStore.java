package io.github.tst_038.minigami.paper.ban;

import io.github.tst_038.minigami.api.ban.Ban;
import io.github.tst_038.minigami.core.storage.Storage;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Keeps the bans in storage. Every call returns at once; the work happens on the storage thread, in the order asked for. */
final class BanStore {

    private final Storage storage;

    BanStore(Storage storage) {
        this.storage = storage;
    }

    /** Deletes the bans over at {@code now} and loads the rest. Completes on the storage thread. */
    CompletableFuture<List<Ban>> load(Instant now) {
        return storage.submit(connection -> {
            try (var delete = connection.prepareStatement("DELETE FROM bans WHERE ends_at <= ?")) {
                delete.setLong(1, now.toEpochMilli());
                delete.executeUpdate();
            }
            var bans = new ArrayList<Ban>();
            try (var select = connection.createStatement();
                    var rows = select.executeQuery("SELECT player, name, scope, ends_at, reason, banned_by, banned_at FROM bans")) {
                while (rows.next()) {
                    var end = rows.getLong(4);
                    var ends = rows.wasNull() ? Optional.<Instant>empty() : Optional.of(Instant.ofEpochMilli(end));
                    bans.add(new Ban(UUID.fromString(rows.getString(1)), rows.getString(2), rows.getString(3), ends,
                            Optional.ofNullable(rows.getString(5)), rows.getString(6), Instant.ofEpochMilli(rows.getLong(7))));
                }
            }
            return bans;
        });
    }

    /** Writes the ban, replacing the player's ban in its scope. Completes on the storage thread. */
    CompletableFuture<Void> save(Ban ban) {
        return storage.<Void>submit(connection -> {
            try (var insert = connection.prepareStatement("INSERT OR REPLACE INTO bans (player, scope, name, ends_at, reason, banned_by, banned_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, ban.player().toString());
                insert.setString(2, ban.scope());
                insert.setString(3, ban.name());
                if (ban.end().isPresent()) {
                    insert.setLong(4, ban.end().get().toEpochMilli());
                } else {
                    insert.setNull(4, Types.INTEGER);
                }
                insert.setString(5, ban.reason().orElse(null));
                insert.setString(6, ban.by());
                insert.setLong(7, ban.at().toEpochMilli());
                insert.executeUpdate();
            }
            return null;
        });
    }

    /** Deletes the player's ban in the scope, or all of theirs if it is empty. Completes on the storage thread. */
    CompletableFuture<Void> delete(UUID player, Optional<String> scope) {
        return storage.<Void>submit(connection -> {
            try (var delete = connection.prepareStatement(scope.isPresent() ? "DELETE FROM bans WHERE player = ? AND scope = ?" : "DELETE FROM bans WHERE player = ?")) {
                delete.setString(1, player.toString());
                if (scope.isPresent()) {
                    delete.setString(2, scope.get());
                }
                delete.executeUpdate();
            }
            return null;
        });
    }
}
