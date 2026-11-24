package io.github.tst_038.minigami.koth.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalletTest {

    private static final Logger LOGGER = Logger.getLogger(WalletTest.class.getName());
    private static final UUID ALICE = UUID.randomUUID();

    @TempDir
    private Path folder;
    private SqliteStorage storage;
    private Wallet wallet;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("koth.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "Test storage", LOGGER);
        // A direct executor stands in for the server thread, so a result is in the cache once its future completes.
        wallet = new Wallet(new PointsLedger(storage), new Balances(), Runnable::run, LOGGER);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void nothingIsKnownUntilLoaded() {
        assertTrue(wallet.balances().of(ALICE).isEmpty());

        assertEquals(0, wallet.balance(ALICE).join());

        assertEquals(OptionalLong.of(0), wallet.balances().of(ALICE));
    }

    @Test
    void creditingAndSpendingUpdateTheCache() {
        wallet.credit(ALICE, 10);
        // Storage keeps the order, so this has run once the spend has.
        assertEquals(OptionalLong.of(4), wallet.spend(ALICE, 6).join());

        assertEquals(OptionalLong.of(4), wallet.balances().of(ALICE));
    }

    @Test
    void aFailedSpendLeavesTheCache() {
        wallet.credit(ALICE, 3);
        wallet.balance(ALICE).join();

        assertEquals(OptionalLong.empty(), wallet.spend(ALICE, 5).join());

        assertEquals(OptionalLong.of(3), wallet.balances().of(ALICE));
    }

    @Test
    void takingUpdatesTheCacheAndFloorsAtZero() {
        wallet.credit(ALICE, 3);

        assertEquals(0, wallet.take(ALICE, 10).join());

        assertEquals(OptionalLong.of(0), wallet.balances().of(ALICE));
    }

    @Test
    void loadingReadsStorageAndForgettingDropsIt() {
        wallet.credit(ALICE, 8);
        wallet.balance(ALICE).join();
        wallet.forget(ALICE);
        assertTrue(wallet.balances().of(ALICE).isEmpty());

        wallet.load(ALICE);
        // Storage keeps the order, so this read has run once the load has.
        wallet.balance(ALICE).join();
        wallet.forget(ALICE);
        wallet.load(ALICE);
        wallet.take(ALICE, 3).join();

        assertEquals(OptionalLong.of(5), wallet.balances().of(ALICE));
    }
}
