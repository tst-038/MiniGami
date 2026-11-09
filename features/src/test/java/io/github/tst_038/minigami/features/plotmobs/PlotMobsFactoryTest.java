package io.github.tst_038.minigami.features.plotmobs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlotMobsFactoryTest {

    private final PlotMobsFactory factory = new PlotMobsFactory();

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        var keys = Setting.keys(factory.settings(), false);

        assertEquals(List.of("max", "break-blocks"), List.copyOf(keys.keySet()));
    }

    @Test
    void readsOnlyDeclaredKeys() throws Exception {
        read("{ max: 4, break-blocks: false }");
    }

    @Test
    void leftOutOptionalSettingsKeepTheirDefaults() throws Exception {
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
