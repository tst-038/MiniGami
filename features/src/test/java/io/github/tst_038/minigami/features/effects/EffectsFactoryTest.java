package io.github.tst_038.minigami.features.effects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EffectsFactoryTest {

    private final EffectsFactory factory = new EffectsFactory();

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        var keys = Setting.keys(factory.settings(), false);

        assertEquals(List.of("<effect>"), List.copyOf(keys.keySet()));
    }

    // No effects listed: looking a name up needs the server's registry, which tests don't have.
    @Test
    void readsOnlyDeclaredKeys() throws Exception {
        read("{}");
    }

    private void read(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        var reader = ConfigReader.strict("games/test.yml", config, factory.settings());

        factory.create(reader);

        assertDoesNotThrow(reader::check);
    }
}
