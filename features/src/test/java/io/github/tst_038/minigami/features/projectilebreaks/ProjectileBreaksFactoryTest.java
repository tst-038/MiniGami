package io.github.tst_038.minigami.features.projectilebreaks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ProjectileBreaksFactoryTest {

    private final ProjectileBreaksFactory factory = new ProjectileBreaksFactory();

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        var keys = Setting.keys(factory.settings(), true);

        assertEquals(List.of("blocks"), List.copyOf(keys.keySet()));
    }

    // Block lists stay empty: telling a block from an item needs the server's registry, which tests don't have.
    @Test
    void readsOnlyDeclaredKeys() {
        assertDoesNotThrow(() -> read("{ blocks: [] }"));
    }

    @Test
    void leftOutOptionalSettingsKeepTheirDefaults() {
        assertDoesNotThrow(() -> read("{ blocks: [] }"));
    }

    private void read(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        var reader = ConfigReader.strict("games/test.yml", config, factory.settings());

        factory.create(reader);

        reader.check();
    }
}
