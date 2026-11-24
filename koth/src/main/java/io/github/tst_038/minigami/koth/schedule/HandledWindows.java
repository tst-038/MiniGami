package io.github.tst_038.minigami.koth.schedule;

import io.github.tst_038.minigami.core.storage.Storage;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Remembers which window was last skipped or stopped, so that a restart doesn't start it after all. Futures complete on the storage thread. */
public final class HandledWindows {

    private final Storage storage;

    public HandledWindows(Storage storage) {
        this.storage = storage;
    }

    /** @return the start of the last window that was skipped or stopped, if any */
    public CompletableFuture<Optional<LocalDateTime>> last() {
        return storage.submit(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT start FROM handled_window WHERE id = 1")) {
                return result.next() ? Optional.of(LocalDateTime.parse(result.getString(1))) : Optional.<LocalDateTime>empty();
            }
        });
    }

    public CompletableFuture<Void> remember(LocalDateTime start) {
        return storage.submit(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO handled_window (id, start) VALUES (1, ?) ON CONFLICT (id) DO UPDATE SET start = excluded.start")) {
                statement.setString(1, start.toString());
                statement.executeUpdate();
                return null;
            }
        });
    }
}
