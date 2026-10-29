package io.github.tst_038.minigami.paper.night;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.tst_038.minigami.gamenight.night.Night;
import io.github.tst_038.minigami.paper.storage.Migrations;
import io.github.tst_038.minigami.paper.storage.SqliteStorage;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NightStoreTest {

    private static final Logger LOGGER = Logger.getLogger(NightStoreTest.class.getName());

    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    @TempDir
    private Path folder;
    private Storage storage;
    private NightStore store;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), LOGGER);
        store = new NightStore(storage, LOGGER);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void aSavedNightKeepsItsTimesAndEveryParticipantsStanding() {
        var id = UUID.randomUUID();
        store.save(new NightStore.Played(id, "friday", "<gold>Friday", Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(5_000),
                List.of(new Night.Result(ann, Optional.of("1"), 25, 1), new Night.Result(bob, Optional.empty(), 7, 2)))).join();

        var night = storage.submit(connection -> {
            try (var rows = connection.createStatement().executeQuery("SELECT template, name, started_at, ended_at FROM nights WHERE id = '" + id + "'")) {
                rows.next();
                return List.of(rows.getString(1), rows.getString(2), rows.getLong(3), rows.getLong(4));
            }
        }).join();
        var players = storage.submit(connection -> {
            var found = new ArrayList<String>();
            try (var rows = connection.createStatement().executeQuery("SELECT player, team, points, place FROM night_players ORDER BY place")) {
                while (rows.next()) {
                    found.add(rows.getString(1) + "|" + rows.getString(2) + "|" + rows.getInt(3) + "|" + rows.getInt(4));
                }
            }
            return found;
        }).join();

        assertEquals(List.of("friday", "<gold>Friday", 1_000L, 5_000L), night);
        assertEquals(List.of(ann + "|1|25|1", bob + "|null|7|2"), players);
    }

    @Test
    void aSoloPlayerHasNoTeamStored() {
        var id = UUID.randomUUID();
        store.save(new NightStore.Played(id, "n", "N", Instant.EPOCH, Instant.EPOCH, List.of(new Night.Result(bob, Optional.empty(), 0, 1)))).join();

        var team = storage.submit(connection -> {
            try (var rows = connection.createStatement().executeQuery("SELECT team FROM night_players")) {
                rows.next();
                return rows.getString(1);
            }
        }).join();

        assertNull(team);
    }
}
