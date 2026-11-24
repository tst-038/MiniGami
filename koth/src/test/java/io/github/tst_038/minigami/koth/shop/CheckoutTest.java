package io.github.tst_038.minigami.koth.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.reward.Reward;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.koth.ledger.Balances;
import io.github.tst_038.minigami.koth.ledger.PointsLedger;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckoutTest {

    private static final Logger LOGGER = Logger.getLogger(CheckoutTest.class.getName());
    private static final UUID ALICE = UUID.randomUUID();

    @TempDir
    private Path folder;
    private SqliteStorage storage;
    private Wallet wallet;
    private final Set<UUID> inMatch = new java.util.HashSet<>();
    private final List<String> given = new ArrayList<>();
    private final List<String> told = new ArrayList<>();
    private boolean online = true;
    private Set<String> permissions = Set.of();
    private Checkout checkout;
    private Player alice;

    @BeforeEach
    void open() throws IOException {
        LOGGER.setLevel(Level.OFF);
        storage = new SqliteStorage(folder.resolve("koth.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "Test storage", LOGGER);
        // A direct executor stands in for the server thread.
        wallet = new Wallet(new PointsLedger(storage), new Balances(), Runnable::run, LOGGER);
        try (var in = getClass().getResourceAsStream("/messages/en.yml")) {
            var messages = new Messages(YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)), LOGGER);
            checkout = new Checkout(messages, wallet, inMatch::contains, Runnable::run, LOGGER);
        }
        alice = player();
        wallet.credit(ALICE, 100);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    private Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> ALICE;
                    case "getName" -> "Alice";
                    case "isOnline" -> online;
                    case "hasPermission" -> permissions.contains((String) args[0]);
                    case "sendMessage" -> {
                        told.add(PlainTextComponentSerializer.plainText().serialize((Component) args[0]));
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Reward reward(String name, boolean fails) {
        return new Reward() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void give(Player player) {
                if (fails) {
                    throw new IllegalStateException("no room");
                }
                given.add(name);
            }
        };
    }

    private ShopEntry entry(int cost, Reward... rewards) {
        return new ShopEntry("thing", 0, cost, Material.DIAMOND, "Thing", List.of(), List.of(rewards), Optional.of("owned.thing"));
    }

    /** Storage keeps the order, so once the second read is back, everything the first one set going has run. */
    private long pointsAfterSettling() {
        wallet.balance(ALICE).join();
        return wallet.balance(ALICE).join();
    }

    @Test
    void aPurchaseTakesThePointsAndGivesEveryReward() {
        checkout.buy(alice, entry(60, reward("one", false), reward("two", false)));

        assertEquals(40, pointsAfterSettling());
        assertEquals(List.of("one", "two"), given);
        assertEquals(1, told.size());
        assertEquals(true, told.getFirst().contains("You bought Thing for 60 points"), told.getFirst());
    }

    @Test
    void tooFewPointsTakeNothingAndGiveNothing() {
        checkout.buy(alice, entry(101, reward("one", false)));

        assertEquals(100, pointsAfterSettling());
        assertEquals(List.of(), given);
        assertEquals(true, told.getFirst().contains("you have 100 points"), told.getFirst());
    }

    @Test
    void aRewardThatCantBeGivenGivesThePointsBack() {
        checkout.buy(alice, entry(60, reward("one", false), reward("two", true)));

        assertEquals(100, pointsAfterSettling());
        assertEquals(true, told.getLast().contains("you kept your points"), told.getLast());
    }

    @Test
    void aBuyerWhoLeftDuringThePurchaseGetsThePointsBack() {
        online = false;

        checkout.buy(alice, entry(60, reward("one", false)));

        assertEquals(100, pointsAfterSettling());
        assertEquals(List.of(), given);
    }

    @Test
    void whoOwnsTheEntryAlreadyPaysNothing() {
        permissions = Set.of("owned.thing");

        checkout.buy(alice, entry(60, reward("one", false)));

        assertEquals(100, pointsAfterSettling());
        assertEquals(List.of(), given);
    }

    @Test
    void aPlayerInAMatchPaysNothing() {
        inMatch.add(ALICE);

        checkout.buy(alice, entry(60, reward("one", false)));

        assertEquals(100, pointsAfterSettling());
        assertEquals(List.of(), given);
    }

    @Test
    void twoPurchasesTogetherCostOnlyAsMuchAsThePointsAllow() {
        checkout.buy(alice, entry(60, reward("one", false)));
        checkout.buy(alice, entry(60, reward("two", false)));

        assertEquals(40, pointsAfterSettling());
        assertEquals(List.of("one"), given);
    }
}
