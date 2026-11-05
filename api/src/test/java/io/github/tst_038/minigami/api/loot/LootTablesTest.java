package io.github.tst_038.minigami.api.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.core.loot.Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LootTablesTest {

    private static final ItemCatalog CATALOG = new ItemCatalog(material -> true, material -> 64, name -> false, name -> false, name -> false);

    @TempDir
    Path directory;

    private LootTables.Loaded load(String... filesAndText) throws Exception {
        var loot = Files.createDirectories(directory.resolve("loot"));
        for (int i = 0; i < filesAndText.length; i += 2) {
            var file = loot.resolve(filesAndText[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, filesAndText[i + 1]);
        }
        return LootTables.load(YamlFiles.readTree(loot), CATALOG);
    }

    private static List<String> problems(LootTables.Loaded loaded) {
        return loaded.problems().stream().map(Object::toString).toList();
    }

    @Test
    void idsArePathsWithoutTheExtension() throws Exception {
        var loaded = load("junk.yml", "entries: [stick]", "castle/a.yml", "entries: [ { table: junk } ]", "castle/deep/b.yml", "entries: [arrow]");

        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("castle/a", "castle/deep/b", "junk"), List.copyOf(loaded.tables().ids()));
        assertEquals(List.of("castle"), List.copyOf(loaded.tables().styles()));
        assertTrue(loaded.tables().hasStyle("castle"));
        assertTrue(!loaded.tables().hasStyle("junk"));
    }

    @Test
    void aTableNamingAMissingTableIsLeftOutAndSoAreTheOnesNamingIt() throws Exception {
        var loaded = load("a.yml", "entries: [ { table: b } ]", "b.yml", "entries: [ { table: ghost } ]", "c.yml", "entries: [stick]");

        assertEquals(List.of("c"), List.copyOf(loaded.tables().ids()));
        assertEquals(List.of(
                "loot/b.yml: names the table 'ghost', which doesn't exist",
                "loot/a.yml: names the table 'b', which doesn't exist"), problems(loaded));
    }

    @Test
    void aTableInAStyleFolderMayNameTablesOfThatStyle() throws Exception {
        var loaded = load("op.yml", "entries: [stick]", "christmas/chest.yml", "entries: [ { table: gift } ]", "christmas/gift.yml", "entries: [cake]");

        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("christmas/chest", "christmas/gift", "op"), List.copyOf(loaded.tables().ids()));
    }

    @Test
    void aCycleIsReportedOnceWithItsPath() throws Exception {
        var loaded = load("a.yml", "entries: [ { table: b } ]", "b.yml", "entries: [ { table: a } ]");

        assertEquals(List.of("loot/a.yml: names itself through a -> b -> a"), problems(loaded));
    }

    @Test
    void aStyleCanMakeACycleThePlainTablesLack() throws Exception {
        var loaded = load("op.yml", "entries: [stick]", "christmas/op.yml", "entries: [ { table: op } ]");

        assertEquals(List.of("loot/christmas/op.yml: names itself through op -> op, in the christmas style"), problems(loaded));
    }

    @Test
    void badFilesAreProblemsAndTheRestStillLoad() throws Exception {
        var loaded = load("Good Name.yml", "entries: [stick]", "bad.yml", "entries: [ { item: unobtainium } ]", "broken.yml", "entries: [unclosed", "fine.yml", "entries: [stick]");

        assertEquals(List.of("fine"), List.copyOf(loaded.tables().ids()));
        assertEquals(3, problems(loaded).size());
        assertTrue(problems(loaded).contains("loot/Good Name.yml: can't be used as a table: name files and folders in lower case letters, digits, _ and -"));
    }

    @Test
    void entriesKeepWhatTheyNamed() throws Exception {
        var loaded = load("junk.yml", "entries: [ { item: stick, weight: 2 }, { empty: true } ]");

        var entries = loaded.tables().find("junk").orElseThrow().pools().getFirst().entries();

        assertEquals(List.of(new Entry.Item<ItemSpec>(new ItemSpec.Vanilla(Material.STICK, io.github.tst_038.minigami.core.range.IntRange.of(1),
                java.util.Optional.empty(), java.util.Map.of(), java.util.Optional.empty()), 2), new Entry.Nothing<ItemSpec>(1)), entries);
    }
}
