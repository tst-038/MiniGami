package io.github.tst_038.minigami.features.decayfloor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DecayFloorFactoryTest {

    private final DecayFloorFactory factory = new DecayFloorFactory();

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        var keys = Setting.keys(factory.settings(), true);

        assertEquals(List.of("delay-ticks", "blocks", "break-below", "fall", "lit", "respawn-grace-ticks"), List.copyOf(keys.keySet()));
        assertEquals(List.of("true", "false"), keys.get("fall"));
    }

    // Empty block lists: telling a block from an item needs the server's registry, which tests don't have.
    @Test
    void readsOnlyDeclaredKeys() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("{ delay-ticks: 8, blocks: [], break-below: [], fall: true, lit: true, respawn-grace-ticks: 20 }");
        var reader = ConfigReader.strict("games/test.yml", config, factory.settings());

        factory.create(reader);

        assertDoesNotThrow(reader::check);
    }

    @Test
    void leftOutOptionalSettingsKeepTheirDefaults() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("{ delay-ticks: 8, blocks: [] }");
        var reader = ConfigReader.strict("games/test.yml", config, factory.settings());

        factory.create(reader);

        assertDoesNotThrow(reader::check);
    }
}
