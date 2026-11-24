package io.github.tst_038.minigami.koth.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.reward.RewardReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ShopReaderTest {

    private static final String ENTRY = """
            title: "Shop"
            rows: 1
            entries:
              a:
                slot: %d
                cost: 5
                icon: { item: diamond, name: "Diamond" }
                rewards: %s
            """;

    private final RewardReader rewards = new RewardReader(name -> Optional.empty());

    private ShopReader.Loaded load(String yaml) {
        return ShopReader.load(yaml, rewards);
    }

    private List<String> problems(String yaml) {
        return load(yaml).problems().stream().map(Object::toString).toList();
    }

    @Test
    void theBundledShopLoadsWithNoProblems() throws IOException {
        try (var in = getClass().getResourceAsStream("/shop.yml")) {
            var loaded = load(new String(Objects.requireNonNull(in, "bundled shop.yml").readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(List.of(), loaded.problems());
            var shop = loaded.shop().orElseThrow();
            assertEquals(3, shop.rows());
            assertEquals(List.of("diamonds", "anvil"), shop.entries().stream().map(ShopEntry::id).toList());
            assertEquals(Optional.of("essentials.anvil"), shop.at(15).orElseThrow().ownedPermission());
        }
    }

    @Test
    void anEntryIsFoundByItsSlot() {
        var shop = load(ENTRY.formatted(3, "[ { item: diamond } ]")).shop().orElseThrow();

        assertEquals(Material.DIAMOND, shop.at(3).orElseThrow().icon());
        assertEquals(5, shop.at(3).orElseThrow().cost());
        assertTrue(shop.at(4).isEmpty());
    }

    @Test
    void twoEntriesInOneSlotAreAProblem() {
        var twice = ENTRY.formatted(2, "[ { item: diamond } ]") + ENTRY.substring(ENTRY.indexOf("  a:")).replace("  a:", "  b:").formatted(2, "[ { item: diamond } ]");

        assertEquals(List.of("shop.yml: entries.b.slot: is used by another entry"), problems(twice));
    }

    @Test
    void aSlotPastTheRowsIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.slot: must be between 0 and 8"), problems(ENTRY.formatted(9, "[ { item: diamond } ]")));
    }

    @Test
    void anEntryWithNoRewardsIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.rewards: needs at least one reward"), problems(ENTRY.formatted(0, "[]")));
    }

    @Test
    void aSavedItemThatIsNotSavedIsReported() {
        var problems = problems(ENTRY.formatted(0, "[ { saved: koth-crate-key } ]"));

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().startsWith("shop.yml: entries.a.rewards[0].saved: no saved item 'koth-crate-key'"), problems.getFirst());
    }

    @Test
    void aFileWithProblemsLoadsNoShop() {
        assertTrue(load(ENTRY.formatted(9, "[]")).shop().isEmpty());
    }

    @Test
    void anOwnedPermissionIsOptional() {
        var shop = load(ENTRY.formatted(0, "[ { item: diamond } ]")).shop().orElseThrow();

        assertEquals(Optional.empty(), shop.at(0).orElseThrow().ownedPermission());
    }
}
