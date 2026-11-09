package io.github.tst_038.minigami.teamdeathmatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TeamDeathmatchSettingsTest {

    private static final Set<String> KEYS = Set.of("fight-seconds", "kills-to-win");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new TeamDeathmatch().settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                fight-seconds: 60
                kills-to-win: 10
                """);
    }

    @Test
    void readsAMinimalConfigStrictly() throws Exception {
        configure("{}");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("teamdeathmatch.yml", yaml, new TeamDeathmatch().settings());
        new TeamDeathmatch().configure(config);
        config.check();
    }
}
