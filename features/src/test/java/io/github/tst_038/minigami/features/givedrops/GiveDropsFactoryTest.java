package io.github.tst_038.minigami.features.givedrops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GiveDropsFactoryTest {

    private final GiveDropsFactory factory = new GiveDropsFactory();

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        assertEquals(List.of("<block>"), List.copyOf(Setting.keys(factory.settings(), false).keySet()));
    }

    // Any block name is a key; unknown names only become problems, which is not what this checks.
    @Test
    void readsOnlyDeclaredKeys() throws Exception {
        assertDoesNotThrow(() -> read("{ one: [], two: [] }"));
    }

    @Test
    void anEmptySectionIsFine() throws Exception {
        assertDoesNotThrow(() -> read("{}"));
    }

    private void read(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);

        factory.create(ConfigReader.strict("games/test.yml", config, factory.settings()));
    }
}
