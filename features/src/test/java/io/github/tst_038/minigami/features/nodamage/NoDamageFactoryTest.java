package io.github.tst_038.minigami.features.nodamage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NoDamageFactoryTest {

    @Test
    void readsCausesAndReportsUnknownOnes() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("causes: [fall, entity-attack, falling]");
        var reader = ConfigReader.of("modes/test.yml", config);

        new NoDamageFactory().create(reader);

        var problems = assertThrows(InvalidConfigException.class, reader::check).problems().stream().map(ConfigProblem::toString).toList();
        assertEquals(List.of("modes/test.yml: causes: unknown damage cause 'falling'"), problems);
    }

    @Test
    void theVoidAndKillCantBeTurnedOff() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("causes: [void, kill]");
        var reader = ConfigReader.of("modes/test.yml", config);

        new NoDamageFactory().create(reader);

        assertEquals(2, assertThrows(InvalidConfigException.class, reader::check).problems().size());
    }

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        var keys = Setting.keys(new NoDamageFactory().settings(), false);

        assertEquals(List.of("causes"), List.copyOf(keys.keySet()));
        assertTrue(keys.get("causes").contains("all"));
    }

    @Test
    void readsOnlyDeclaredKeys() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("{ causes: [all, fall] }");
        var reader = ConfigReader.strict("games/test.yml", config, new NoDamageFactory().settings());

        new NoDamageFactory().create(reader);

        assertDoesNotThrow(reader::check);
    }
}
