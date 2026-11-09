package io.github.tst_038.minigami.features.anticamping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class AntiCampingFactoryTest {

    private final AntiCampingFactory factory = new AntiCampingFactory();

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        var keys = Setting.keys(factory.settings(), false);

        assertEquals(List.of("seconds", "blocks"), List.copyOf(keys.keySet()));
    }

    // Empty block list: telling a block from an item needs the server's registry, which tests don't have.
    @Test
    void readsOnlyDeclaredKeys() throws Exception {
        read("{ seconds: 3, blocks: [] }");
    }

    private void read(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        var reader = ConfigReader.strict("games/test.yml", config, factory.settings());

        factory.create(reader);

        assertDoesNotThrow(reader::check);
    }
}
