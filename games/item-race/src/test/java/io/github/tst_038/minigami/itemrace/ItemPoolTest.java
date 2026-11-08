package io.github.tst_038.minigami.itemrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ItemPoolTest {

    private static final String ITEMS = """
            items:
              overworld: [ { item: diamond_block, weight: 3 }, cake ]
              nether: [ blaze_rod ]
              end: [ shulker_shell ]
            """;

    // The real check needs the server's registry; the fake says water is not an item.
    private static final java.util.function.Predicate<Material> ITEM = m -> m != Material.WATER;

    private static ConfigReader reader(String yaml) throws InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return ConfigReader.of("item-race.yml", configuration);
    }

    private static ItemPool read(String yaml) throws Exception {
        var config = reader(yaml);
        var pool = ItemPool.read(config, ITEM);
        config.check();
        return pool.orElseThrow();
    }

    private static String problem(String yaml) throws Exception {
        var config = reader(yaml);
        ItemPool.read(config, ITEM);
        return assertThrows(InvalidConfigException.class, config::check).getMessage();
    }

    private static HashSet<Material> allPicked(ItemPool pool) {
        var random = new Random(1);
        var seen = new HashSet<Material>();
        for (int i = 0; i < 500; i++) {
            seen.add(pool.pick(random));
        }
        return seen;
    }

    @Test
    void theSameSeedPicksTheSameItem() throws Exception {
        var pool = read(ITEMS);

        for (long seed = 0; seed < 20; seed++) {
            assertEquals(pool.pick(new Random(seed)), pool.pick(new Random(seed)));
        }
    }

    @Test
    void weightsAreRoughlyRespected() {
        var pool = new ItemPool(List.of(new ItemPool.Entry(Material.CAKE, 1), new ItemPool.Entry(Material.DIAMOND_BLOCK, 3)));
        var random = new Random(42);
        var counts = new EnumMap<Material, Integer>(Material.class);

        for (int i = 0; i < 40_000; i++) {
            counts.merge(pool.pick(random), 1, Integer::sum);
        }

        double ratio = (double) counts.get(Material.DIAMOND_BLOCK) / counts.get(Material.CAKE);
        assertTrue(ratio > 2.8 && ratio < 3.2, "ratio was " + ratio);
    }

    @Test
    void weightDefaultsToOneAndPlainTextIsAnItem() throws Exception {
        var pool = read("items:\n  overworld: [ cake, { item: bread } ]\n");

        assertEquals(2, pool.size());
        assertEquals(java.util.Set.of(Material.CAKE, Material.BREAD), allPicked(pool));
    }

    @Test
    void withoutDimensionsTheEndIsLeftOut() throws Exception {
        assertEquals(java.util.Set.of(Material.DIAMOND_BLOCK, Material.CAKE, Material.BLAZE_ROD), allPicked(read(ITEMS)));
    }

    @Test
    void dimensionsFilterTheItems() throws Exception {
        assertEquals(java.util.Set.of(Material.BLAZE_ROD, Material.SHULKER_SHELL), allPicked(read(ITEMS + "dimensions: [nether, end]\n")));
    }

    @Test
    void excludedItemsAreNeverPicked() throws Exception {
        var picked = allPicked(read(ITEMS + "exclude: [cake, blaze_rod]\n"));

        assertEquals(java.util.Set.of(Material.DIAMOND_BLOCK), picked);
    }

    @Test
    void anEmptyPoolAfterFilteringIsAProblem() throws Exception {
        assertTrue(problem(ITEMS + "dimensions: [nether]\nexclude: [blaze_rod]\n").contains("no item is left"));
        assertThrows(IllegalArgumentException.class, () -> new ItemPool(List.of()));
    }

    @Test
    void invalidInputIsReportedWithItsKey() throws Exception {
        assertTrue(problem("items:\n  overworld: [ { item: cake, weight: 0 } ]\n").contains("items.overworld[0].weight"));
        assertTrue(problem("items:\n  overworld: [ { item: cake, weight: lots } ]\n").contains("whole number"));
        assertTrue(problem("items:\n  overworld: [ nonsense ]\n").contains("unknown item 'nonsense'"));
        assertTrue(problem("items:\n  overworld: [ water ]\n").contains("items.overworld[0].item"));
        assertTrue(problem("items:\n  overworld: [ water ]\n").contains("not an item"));
        assertTrue(problem("items:\n  overworld: [ { weight: 2 } ]\n").contains("items.overworld[0].item"));
        assertTrue(problem("items:\n  moon: [ cake ]\n").contains("unknown dimension"));
        assertTrue(problem(ITEMS + "dimensions: [moon]\n").contains("unknown dimension 'moon'"));
        assertTrue(problem(ITEMS + "exclude: [nonsense]\n").contains("unknown item or block 'nonsense'"));
        assertTrue(problem("dimensions: [overworld]\n").contains("items"));
        assertTrue(problem(ITEMS + "typo: 1\n").contains("unknown key"));
    }

    @Test
    void severalDifferentItemsCanBePickedAtOnce() throws Exception {
        var pool = read(ITEMS);

        var picked = pool.pick(new Random(1), 2);

        assertEquals(2, picked.size());
        assertEquals(2, new HashSet<>(picked).size());
        assertEquals(3, pool.pick(new Random(1), 10).size());
    }
}
