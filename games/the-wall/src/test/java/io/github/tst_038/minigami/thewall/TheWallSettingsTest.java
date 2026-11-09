package io.github.tst_038.minigami.thewall;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TheWallSettingsTest {

    private static final Set<String> KEYS = Set.of("prepare-seconds", "border", "border.end-size", "border.shrink-seconds", "border.damage");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new TheWall().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                prepare-seconds: 60
                border: { end-size: 10, shrink-seconds: 100, damage: 2 }
                """);
    }

    @Test
    void readsAnEmptyConfigStrictly() throws Exception {
        configure("{}");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("thewall.yml", yaml, new TheWall().settings());
        new TheWall().configure(config);
        config.check();
    }
}
