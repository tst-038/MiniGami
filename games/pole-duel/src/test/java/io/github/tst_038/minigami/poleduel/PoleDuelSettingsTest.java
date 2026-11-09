package io.github.tst_038.minigami.poleduel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PoleDuelSettingsTest {

    private static final Set<String> KEYS = Set.of("pole-height", "min-distance", "pole-material", "slab-material", "slabs", "resupply-seconds", "max-players");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new PoleDuel().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                pole-height: 8
                min-distance: 5
                pole-material: oak_log
                slab-material: stone_slab
                slabs: 10
                resupply-seconds: 3
                max-players: 8
                """);
    }

    @Test
    void readsAnEmptyConfigStrictly() throws Exception {
        configure("{}");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("poleduel.yml", yaml, new PoleDuel().settings());
        new PoleDuel().configure(config);
        config.check();
    }
}
