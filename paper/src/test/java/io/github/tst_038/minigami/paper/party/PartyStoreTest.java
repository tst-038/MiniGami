package io.github.tst_038.minigami.paper.party;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.core.party.Parties.Party;
import io.github.tst_038.minigami.paper.storage.Migrations;
import io.github.tst_038.minigami.paper.storage.SqliteStorage;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PartyStoreTest {

    private static final Logger LOGGER = Logger.getLogger(PartyStoreTest.class.getName());

    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID cy = UUID.randomUUID();
    @TempDir
    private Path folder;
    private Storage storage;
    private PartyStore store;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), LOGGER);
        store = new PartyStore(storage, LOGGER);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void aSavedPartyLoadsBackWithItsLeaderAndMemberOrder() {
        var party = new Party(UUID.randomUUID(), bob, List.of(cy, bob, ann));

        store.save(party);

        assertEquals(List.of(party), store.loadAll().join());
    }

    @Test
    void savingAgainReplacesTheMembersAndDeletingRemovesTheParty() {
        var id = UUID.randomUUID();
        store.save(new Party(id, ann, List.of(ann, bob, cy)));
        store.save(new Party(id, bob, List.of(bob, cy)));

        assertEquals(List.of(new Party(id, bob, List.of(bob, cy))), store.loadAll().join());

        store.delete(id);

        assertEquals(List.of(), store.loadAll().join());
    }

    @Test
    void aPlayerCanMoveToAnotherPartyOnceTheOldOneWasSaved() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        store.save(new Party(first, ann, List.of(ann, bob)));
        store.delete(first);
        store.save(new Party(second, cy, List.of(cy, bob)));

        assertEquals(List.of(new Party(second, cy, List.of(cy, bob))), store.loadAll().join());
    }
}
