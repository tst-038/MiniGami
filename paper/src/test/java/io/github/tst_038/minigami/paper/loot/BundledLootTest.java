package io.github.tst_038.minigami.paper.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.loot.ItemCatalog;
import io.github.tst_038.minigami.api.loot.LootTables;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BundledLootTest {

    @Test
    void theExampleTablesAreValid() {
        var enchantments = Set.of("sharpness", "protection", "power", "infinity");
        var catalog = new ItemCatalog(material -> true, material -> 64, enchantments::contains, name -> true, name -> false);

        var loaded = LootTables.load(YamlFiles.readTree(Path.of("src/main/resources/loot")), catalog);

        assertEquals(List.of(), loaded.problems());
        assertEquals(List.of("christmas", "old-rares"), List.copyOf(loaded.tables().styles()));
        assertEquals(List.of("chest", "christmas/junk", "christmas/rare", "common", "junk", "old-rares/op", "op", "rare"), List.copyOf(loaded.tables().ids()));
    }
}
