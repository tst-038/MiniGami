package io.github.tst_038.minigami.paper.ban;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.ban.Ban;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BanStoreTest {

    private static final Logger LOGGER = Logger.getLogger(BanStoreTest.class.getName());
    private static final Instant NOW = Instant.ofEpochMilli(1_790_000_000_000L);

    private final UUID ann = UUID.randomUUID();
    @TempDir
    private Path folder;
    private Storage storage;
    private BanStore store;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "Test storage", LOGGER);
        store = new BanStore(storage);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void savedBansLoadBackAndEndedOnesAreDropped() {
        var forever = new Ban(ann, "Ann", "nights", Optional.empty(), Optional.of("spam"), "Staff", NOW);
        var week = new Ban(ann, "Ann", "game:spleef", Optional.of(NOW.plus(Duration.ofDays(7))), Optional.empty(), "Console", NOW);
        var over = new Ban(ann, "Ann", "koth", Optional.of(NOW.minusSeconds(1)), Optional.empty(), "Staff", NOW.minusSeconds(60));
        store.save(forever);
        store.save(week);
        store.save(over);

        assertEquals(Set.of(forever, week), Set.copyOf(store.load(NOW).join()));
    }

    @Test
    void savingAgainReplacesAndDeletingTakesOneScopeOrAll() {
        store.save(new Ban(ann, "Ann", "nights", Optional.empty(), Optional.empty(), "Staff", NOW));
        var again = new Ban(ann, "Ann", "nights", Optional.of(NOW.plusSeconds(60)), Optional.of("again"), "Staff", NOW);
        store.save(again);
        store.save(new Ban(ann, "Ann", "all", Optional.empty(), Optional.empty(), "Staff", NOW));

        store.delete(ann, Optional.of("all"));
        assertEquals(List.of(again), store.load(NOW).join());

        store.delete(ann, Optional.empty());
        assertEquals(List.of(), store.load(NOW).join());
    }
}
