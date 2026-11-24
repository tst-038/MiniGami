package io.github.tst_038.minigami.koth.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.koth.ledger.PointsLedger;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandledWindowsTest {

    @TempDir
    private Path folder;

    @Test
    void rememberedWindowsSurviveReopeningAndTheLatestWins() {
        var first = LocalDateTime.of(2026, 3, 2, 20, 0);
        var second = LocalDateTime.of(2026, 3, 3, 20, 0);
        try (var storage = open()) {
            var windows = new HandledWindows(storage);
            assertEquals(Optional.empty(), windows.last().join());

            windows.remember(first).join();
            windows.remember(second).join();
        }
        try (var storage = open()) {
            assertEquals(Optional.of(second), new HandledWindows(storage).last().join());
        }
    }

    private SqliteStorage open() {
        return new SqliteStorage(folder.resolve("koth.db"), new Migrations(getClass().getClassLoader(), PointsLedger.MIGRATIONS), "Test storage", Logger.getLogger("test"));
    }
}
