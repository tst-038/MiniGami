package io.github.tst_038.minigami.king;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class KingSettingsTest {

    private static final Set<String> KEYS = Set.of("fight-seconds");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new King().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                fight-seconds: 60
                """);
    }

    @Test
    void readsAnEmptyConfigStrictly() throws Exception {
        configure("{}");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("king.yml", yaml, new King().settings());
        new King().configure(config);
        config.check();
    }
}
