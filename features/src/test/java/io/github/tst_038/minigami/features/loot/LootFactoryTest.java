package io.github.tst_038.minigami.features.loot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.loot.ItemCatalog;
import io.github.tst_038.minigami.api.loot.LootTables;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LootFactoryTest {

    @TempDir
    Path directory;

    private LootFactory factory() throws Exception {
        var loot = Files.createDirectories(directory.resolve("loot/christmas"));
        Files.writeString(directory.resolve("loot/rare.yml"), "entries: [stick]");
        Files.writeString(loot.resolve("rare.yml"), "entries: [arrow]");
        var catalog = new ItemCatalog(material -> true, material -> 64, name -> false, name -> false, name -> false);
        var tables = LootTables.load(YamlFiles.readTree(directory.resolve("loot")), catalog).tables();
        return new LootFactory(() -> tables, name -> Optional.empty());
    }

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("games/test.yml", config);
    }

    @Test
    void worksWithoutAStyle() throws Exception {
        var root = root("loot: {}");

        var loot = factory().create(root.section("loot"));

        assertDoesNotThrow(root::check);
        assertTrue(loot.changesBlocks());
    }

    @Test
    void acceptsAStyleThatIsAFolder() throws Exception {
        var root = root("loot: { style: christmas }");

        factory().create(root.section("loot"));

        assertDoesNotThrow(root::check);
    }

    @Test
    void rejectsAStyleThatIsNoFolderAndNamesTheOnesThatAre() throws Exception {
        var root = root("loot: { style: easter }");

        factory().create(root.section("loot"));

        var problems = assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("no loot style 'easter'") && problems.getFirst().contains("[christmas]"), problems.toString());
    }

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() throws Exception {
        assertEquals(List.of("style"), List.copyOf(Setting.keys(factory().settings(), true).keySet()));
    }

    @Test
    void readsOnlyDeclaredKeys() throws Exception {
        assertDoesNotThrow(() -> readStrict(factory(), "{ style: christmas }"));
    }

    @Test
    void leftOutOptionalSettingsKeepTheirDefaults() throws Exception {
        assertDoesNotThrow(() -> readStrict(factory(), "{}"));
    }

    private void readStrict(LootFactory factory, String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        var reader = ConfigReader.strict("games/test.yml", config, factory.settings());

        factory.create(reader);

        reader.check();
    }
}
