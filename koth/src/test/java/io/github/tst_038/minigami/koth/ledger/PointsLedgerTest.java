package io.github.tst_038.minigami.koth.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PointsLedgerTest {

    private static final Logger LOGGER = Logger.getLogger(PointsLedgerTest.class.getName());
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @TempDir
    private Path folder;
    private final List<Storage> opened = new ArrayList<>();

    private PointsLedger open() {
        var storage = new SqliteStorage(folder.resolve("koth.db"), new Migrations(getClass().getClassLoader(), PointsLedger.MIGRATIONS), "Test storage", LOGGER);
        opened.add(storage);
        return new PointsLedger(storage);
    }

    @AfterEach
    void close() {
        opened.forEach(Storage::close);
    }

    @Test
    void anUnknownPlayerHasNoPointsAndAddsAccumulate() {
        var ledger = open();

        assertEquals(0, ledger.balance(ALICE).join());
        assertEquals(5, ledger.add(ALICE, 5).join());
        assertEquals(12, ledger.add(ALICE, 7).join());
        assertEquals(12, ledger.balance(ALICE).join());
        assertEquals(0, ledger.balance(BOB).join());
    }

    @Test
    void spendingTakesThePointsOnlyWhenThereAreEnough() {
        var ledger = open();
        ledger.add(ALICE, 10).join();

        assertEquals(OptionalLong.of(4), ledger.spend(ALICE, 6).join());
        assertEquals(OptionalLong.empty(), ledger.spend(ALICE, 5).join());
        assertEquals(4, ledger.balance(ALICE).join());
        assertEquals(OptionalLong.empty(), ledger.spend(BOB, 1).join());
        assertEquals(OptionalLong.of(0), ledger.spend(ALICE, 4).join());
    }

    @Test
    void twoQueuedSpendsOfSixtyAgainstOneHundredLetExactlyOneThrough() {
        var ledger = open();
        ledger.add(ALICE, 100).join();

        var first = ledger.spend(ALICE, 60);
        var second = ledger.spend(ALICE, 60);

        assertEquals(1, List.of(first.join(), second.join()).stream().filter(OptionalLong::isPresent).count());
        assertEquals(40, ledger.balance(ALICE).join());
    }

    @Test
    void spendsFromSeveralThreadsNeverOverdraw() {
        var ledger = open();
        ledger.add(ALICE, 100).join();

        var spends = new ArrayList<CompletableFuture<OptionalLong>>();
        for (int i = 0; i < 20; i++) {
            spends.add(CompletableFuture.supplyAsync(() -> ledger.spend(ALICE, 30).join()));
        }
        var succeeded = spends.stream().filter(spend -> spend.join().isPresent()).count();

        assertEquals(3, succeeded);
        assertEquals(10, ledger.balance(ALICE).join());
    }

    @Test
    void takingFloorsAtZero() {
        var ledger = open();
        ledger.add(ALICE, 10).join();

        assertEquals(7, ledger.take(ALICE, 3).join());
        assertEquals(0, ledger.take(ALICE, 50).join());
        assertEquals(0, ledger.balance(ALICE).join());
        assertEquals(0, ledger.take(BOB, 5).join());
    }

    @Test
    void anAmountThatIsNotPositiveIsRefused() {
        var ledger = open();

        assertThrows(IllegalArgumentException.class, () -> ledger.add(ALICE, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.spend(ALICE, -1));
        assertThrows(IllegalArgumentException.class, () -> ledger.take(ALICE, 0));
    }

    @Test
    void theBalanceSurvivesAReopen() {
        var first = open();
        first.add(ALICE, 42).join();
        opened.getFirst().close();

        var second = open();

        assertEquals(42, second.balance(ALICE).join());
    }
}
