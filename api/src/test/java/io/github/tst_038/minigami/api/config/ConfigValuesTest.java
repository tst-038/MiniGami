package io.github.tst_038.minigami.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigValuesTest {

    @Test
    void valuesAreReadAsYaml() {
        assertEquals(Optional.of(5), ConfigValues.parse("5"));
        assertEquals(Optional.of(true), ConfigValues.parse("true"));
        assertEquals(Optional.of("<gold>Void Fall"), ConfigValues.parse("<gold>Void Fall"));
        assertEquals(Optional.of("in-place"), ConfigValues.parse("\"in-place\""));
        assertEquals(Optional.of(List.of("winter", "summer")), ConfigValues.parse("[winter, summer]"));
        assertEquals(Optional.of(Map.of("void-kills", Map.of("y", 0))), ConfigValues.parse("{ void-kills: { y: 0 } }"));
    }

    @Test
    void whatIsNoValueIsRefused() {
        assertTrue(ConfigValues.parse("[unclosed").isEmpty());
        assertTrue(ConfigValues.parse("a: b").isEmpty());
        assertTrue(ConfigValues.parse("# only a comment").isEmpty());
        // A comment would quietly cut the text short; in quotes the # is kept.
        assertTrue(ConfigValues.parse("Night #1").isEmpty());
        assertEquals(Optional.of("Night #1"), ConfigValues.parse("\"Night #1\""));
    }

    @Test
    void aTextMayStartWithAColourCode() {
        assertEquals(Optional.of("&c&lYuletide Crate"), ConfigValues.parse("&c&lYuletide Crate"));
    }

    @Test
    void aTypedTextLosesItsQuotesAndKeepsTheRest() {
        assertEquals("<gold>Game Night: Finals", ConfigValues.text("\"<gold>Game Night: Finals\""));
        assertEquals("Game Night: Finals", ConfigValues.text("Game Night: Finals"));
        assertEquals("Night #1", ConfigValues.text("Night #1"));
        assertEquals("2026", ConfigValues.text("2026"));
        assertEquals("&cCrate", ConfigValues.text("&cCrate"));
    }

    @Test
    void aSingleValueBecomesAListWhereTheKeyWantsOne() {
        var file = new YamlConfiguration();
        file.set("causes", List.of("fall"));
        assertEquals(List.of("all"), ConfigValues.forKey(file, "causes", "all",
                probe -> probe.isList("causes") ? List.of() : List.of(new ConfigProblem("config.yml", "causes", "must be a list of text"))));
        assertEquals(5, ConfigValues.forKey(file, "lives", 5, probe -> List.of()));
        assertEquals(List.of("a", "b"), ConfigValues.forKey(file, "causes", List.of("a", "b"), probe -> List.of()));
    }

    @Test
    void showsValuesAsInlineYaml() {
        var location = new LinkedHashMap<String, Object>();
        location.put("world", "world");
        location.put("x", 1.23456);
        location.put("y", 64);
        assertEquals("{world: world, x: 1.23, y: 64}", ConfigValues.show(location));
        assertEquals("[Rocket, Tree]", ConfigValues.show(List.of("Rocket", "Tree")));
        assertEquals("{y: 0}", ConfigValues.show(Map.of("y", 0)));
        assertEquals("true", ConfigValues.show(true));
    }
}
