package io.github.tst_038.minigami.api.loot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.core.loot.Entry;
import io.github.tst_038.minigami.core.range.IntRange;
import io.github.tst_038.minigami.core.loot.LootTable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LootReaderTest {

    private final LootReader reader = new LootReader(new ItemCatalog(
            material -> material != Material.WATER,
            material -> material == Material.IRON_SWORD ? 1 : 64,
            name -> name.equals("sharpness") || name.equals("unbreaking"),
            name -> name.equals("healing"),
            name -> name.equals("trident-of-doom")));

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("loot/test.yml", config);
    }

    private static List<String> problems(ConfigReader root) {
        return assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
    }

    private static Entry.Item<ItemSpec> item(ItemSpec spec, int weight) {
        return new Entry.Item<>(spec, weight);
    }

    private static ItemSpec.Vanilla plain(Material material, IntRange amount) {
        return new ItemSpec.Vanilla(material, amount, Optional.empty(), Map.of(), Optional.empty());
    }

    @Test
    void aTableOfOnePoolCanBeWrittenAtTheTop() throws Exception {
        var root = root("rolls: 2-3\nentries:\n  - { item: arrow, amount: 8-16, weight: 3 }\n  - iron_sword\n");

        var table = reader.read(root).orElseThrow();

        assertDoesNotThrow(root::check);
        var pool = table.pools().getFirst();
        assertEquals(new IntRange(2, 3), pool.rolls());
        assertEquals(100.0, pool.chance());
        assertEquals(List.of(item(plain(Material.ARROW, new IntRange(8, 16)), 3), item(plain(Material.IRON_SWORD, IntRange.of(1)), 1)), pool.entries());
    }

    @Test
    void poolsEachHaveTheirOwnRollsAndChance() throws Exception {
        var root = root("""
                pools:
                  - rolls: 4
                    entries: [ { table: junk, weight: 60 }, { table: castle/common, weight: 40 } ]
                  - chance: 25
                    entries: [ { table: op }, { empty: true, weight: 2 } ]
                """);

        var table = reader.read(root).orElseThrow();

        assertDoesNotThrow(root::check);
        assertEquals(2, table.pools().size());
        assertEquals(IntRange.of(4), table.pools().get(0).rolls());
        assertEquals(List.of(new Entry.Table<ItemSpec>("junk", 60), new Entry.Table<ItemSpec>("castle/common", 40)), table.pools().get(0).entries());
        assertEquals(IntRange.of(1), table.pools().get(1).rolls());
        assertEquals(25.0, table.pools().get(1).chance());
        assertEquals(List.of(new Entry.Table<ItemSpec>("op", 1), new Entry.Nothing<ItemSpec>(2)), table.pools().get(1).entries());
    }

    @Test
    void itemsCanBeNamedEnchantedAndBrewed() throws Exception {
        var root = root("""
                entries:
                  - item: iron_sword
                    name: "<red>Sword of Testing"
                    enchants: { sharpness: 3-5, unbreaking: 2 }
                  - { item: potion, potion: healing }
                  - { saved: trident-of-doom, amount: 2 }
                """);

        var entries = reader.read(root).orElseThrow().pools().getFirst().entries();

        assertDoesNotThrow(root::check);
        assertEquals(item(new ItemSpec.Vanilla(Material.IRON_SWORD, IntRange.of(1), Optional.of("<red>Sword of Testing"),
                Map.of("sharpness", new IntRange(3, 5), "unbreaking", IntRange.of(2)), Optional.empty()), 1), entries.get(0));
        assertEquals(item(new ItemSpec.Vanilla(Material.POTION, IntRange.of(1), Optional.empty(), Map.of(), Optional.of("healing")), 1), entries.get(1));
        assertEquals(item(new ItemSpec.Saved("trident-of-doom", IntRange.of(2)), 1), entries.get(2));
    }

    @Test
    void badItemsAreProblemsAndLeftOut() throws Exception {
        var root = root("""
                entries:
                  - { item: dirt }
                  - { item: unobtainium }
                  - { item: water }
                  - { item: arrow, amount: 65 }
                  - { item: iron_sword, amount: 2 }
                  - { item: arrow, amount: many }
                  - { item: arrow, amount: 5-2 }
                  - { item: arrow, weight: 0 }
                  - { item: bow, enchants: { sharpnes: 1, unbreaking: 0 } }
                  - { item: potion, potion: harming }
                  - { saved: nothing-here }
                """);

        var table = reader.read(root);

        assertEquals(List.of(
                "loot/test.yml: entries[1].item: unknown item 'unobtainium'",
                "loot/test.yml: entries[2].item: 'water' is a block that can't be held as an item",
                "loot/test.yml: entries[3].amount: must be a number or a range like 2-4, from 1 to 64",
                "loot/test.yml: entries[4].amount: must be a number or a range like 2-4, from 1 to 1",
                "loot/test.yml: entries[5].amount: must be a number or a range like 2-4, from 1 to 64",
                "loot/test.yml: entries[6].amount: must be a number or a range like 2-4, from 1 to 64",
                "loot/test.yml: entries[7].weight: must be between 1 and 1000000",
                "loot/test.yml: entries[8].enchants.sharpnes: unknown enchantment",
                "loot/test.yml: entries[8].enchants.unbreaking: must be a number or a range like 2-4, from 1 to 255",
                "loot/test.yml: entries[9].potion: unknown potion type 'harming'",
                "loot/test.yml: entries[10].saved: no saved item 'nothing-here'; save the item you hold with /mga loot save nothing-here"),
                problems(root));
        assertTrue(table.isPresent());
    }

    @Test
    void anEntryMustBeExactlyOneKind() throws Exception {
        var root = root("entries:\n  - { weight: 2 }\n  - { item: arrow, table: junk }\n  - { empty: false }\n  - { table: Bad Id }\n");

        reader.read(root);

        assertEquals(List.of(
                "loot/test.yml: entries[0]: needs one of item, saved, table or empty",
                "loot/test.yml: entries[1]: can only have one of item, saved, table or empty, but has item and table",
                "loot/test.yml: entries[2].empty: must be true; leave it out for an entry that gives something",
                "loot/test.yml: entries[3].table: 'Bad Id' is not a table id; use lower case letters, digits, _ and -, with / between folders"),
                problems(root));
    }

    @Test
    void badPoolSettingsAreProblems() throws Exception {
        var root = root("pools:\n  - { chance: 0, rolls: -1, entries: [arrow] }\n  - { chance: 101, entries: [arrow] }\n");

        var table = reader.read(root);

        assertEquals(List.of(
                "loot/test.yml: pools[0].rolls: must be a number or a range like 2-4, from 0 to 100",
                "loot/test.yml: pools[0].chance: must be above 0 and at most 100 (a percentage)",
                "loot/test.yml: pools[1].chance: must be above 0 and at most 100 (a percentage)"), problems(root));
        assertEquals(2, table.orElseThrow().pools().size());
    }

    @Test
    void aTableWithNothingUsableHasNoTable() throws Exception {
        var root = root("entries: [ { item: unobtainium } ]");

        assertEquals(Optional.<LootTable<ItemSpec>>empty(), reader.read(root));
        assertEquals(1, problems(root).size());
    }

    @Test
    void unknownKeysAreReported() throws Exception {
        var root = root("entries: [ { item: arrow, ammount: 3 } ]\nrole: 2\n");

        reader.read(root);

        assertEquals(List.of("loot/test.yml: role: unknown key", "loot/test.yml: entries[0].ammount: unknown key"), problems(root));
    }
}
