package io.github.tst_038.minigami.hideandseek;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HideAndSeekSettingsTest {

    private static final Set<String> KEYS = Set.of("hide-seconds", "seek-seconds");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new HideAndSeek().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                hide-seconds: 20
                seek-seconds: 100
                """);
    }

    @Test
    void readsAMinimalConfigStrictly() throws Exception {
        configure("{}");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("hideandseek.yml", yaml, new HideAndSeek().settings());
        new HideAndSeek().configure(config);
        config.check();
    }
}
