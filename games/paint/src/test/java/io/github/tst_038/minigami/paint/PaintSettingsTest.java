package io.github.tst_038.minigami.paint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PaintSettingsTest {

    private static final Set<String> KEYS = Set.of("play-seconds", "paintable", "colors");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new Paint().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                play-seconds: 60
                paintable: [white_concrete, stone]
                colors: [red_concrete, blue_concrete]
                """);
    }

    @Test
    void readsAnEmptyConfigStrictly() throws Exception {
        configure("{}");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("paint.yml", yaml, new Paint().settings());
        new Paint().configure(config);
        config.check();
    }
}
