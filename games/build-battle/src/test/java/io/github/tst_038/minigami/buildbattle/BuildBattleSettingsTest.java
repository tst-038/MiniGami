package io.github.tst_038.minigami.buildbattle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BuildBattleSettingsTest {

    private static final Set<String> KEYS = Set.of("build-seconds", "vote-seconds", "themes");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new BuildBattle().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                build-seconds: 60
                vote-seconds: 10
                themes: [castle, boat]
                """);
    }

    @Test
    void readsAMinimalConfigStrictly() throws Exception {
        configure("{}");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("buildbattle.yml", yaml, new BuildBattle().settings());
        new BuildBattle().configure(config);
        config.check();
    }
}
