package io.github.tst_038.minigami.domination;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DominationSettingsTest {

    private static final Set<String> KEYS = Set.of("capture-points", "capture-seconds", "keep-captured", "play-seconds", "points-to-win");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new Domination().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                capture-points: { a: "<red>A", b: "<blue>B" }
                capture-seconds: 3
                keep-captured: false
                play-seconds: 120
                points-to-win: 50
                """);
    }

    @Test
    void readsAMinimalConfigStrictly() throws Exception {
        configure("capture-points: { a: A }");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("domination.yml", yaml, new Domination().settings());
        new Domination().configure(config);
        config.check();
    }
}
