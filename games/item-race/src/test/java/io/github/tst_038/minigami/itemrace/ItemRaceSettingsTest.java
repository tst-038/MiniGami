package io.github.tst_038.minigami.itemrace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ItemRaceSettingsTest {

    private static final Set<String> KEYS = Set.of("items", "dimensions", "exclude", "play-seconds", "targets", "goal", "hand-in",
            "hand-in-delay-seconds", "border", "border.end-size", "border.shrink-seconds", "border.damage");

    @Test
    void declaresEveryKeyItReads() {
        assertTrue(Setting.keys(new ItemRace(m -> true).settings(), false).keySet().containsAll(KEYS));
    }

    @Test
    void readsAFullConfigStrictly() throws Exception {
        configure("""
                items:
                  overworld: [ { item: diamond_block, weight: 2 }, cake ]
                  nether: [ blaze_rod ]
                dimensions: [overworld, nether]
                exclude: [ cake ]
                play-seconds: 600
                targets: 2
                goal: any
                hand-in: click
                hand-in-delay-seconds: 3
                border: { end-size: 10, shrink-seconds: 100, damage: 2 }
                """);
    }

    @Test
    void readsAMinimalConfigStrictly() throws Exception {
        configure("items: { overworld: [ cake ] }");
    }

    private static void configure(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        var config = ConfigReader.strict("item-race.yml", yaml, new ItemRace(m -> true).settings());
        new ItemRace(m -> true).configure(config);
        config.check();
    }
}
