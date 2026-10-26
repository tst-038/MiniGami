package io.github.tst_038.minigami.paper.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTest {

    @TempDir
    Path directory;

    private SnapshotStore store;
    private final UUID id = UUID.randomUUID();

    private SnapshotStore store() {
        store = new SnapshotStore(directory, Logger.getAnonymousLogger());
        return store;
    }

    @AfterEach
    void closeStore() {
        store.close();
    }

    @Test
    void loadsWhatWasSaved() {
        var snapshot = PlayerSnapshotTest.sample();
        store().save(id, snapshot);

        assertEquals(Optional.of(snapshot), store.load(id));
    }

    @Test
    void savedSnapshotSurvivesARestart() {
        var snapshot = PlayerSnapshotTest.sample();
        store().save(id, snapshot);
        store.close();

        assertEquals(Optional.of(snapshot), store().load(id));
    }

    @Test
    void deletedSnapshotIsGone() {
        store().save(id, PlayerSnapshotTest.sample());
        store.delete(id);

        assertEquals(Optional.empty(), store.load(id));
    }

    @Test
    void playerWithoutSnapshotHasNone() {
        assertEquals(Optional.empty(), store().load(id));
    }

    @Test
    void leavesNoTemporaryFilesBehind() throws IOException {
        store().save(id, PlayerSnapshotTest.sample());
        store.close();

        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void unreadableFileIsReportedNotIgnored() throws IOException {
        Files.writeString(directory.resolve(id + ".yml"), "version: 1\n");

        assertThrows(IllegalStateException.class, () -> store().load(id));
        assertTrue(Files.exists(directory.resolve(id + ".yml")));
    }
}
