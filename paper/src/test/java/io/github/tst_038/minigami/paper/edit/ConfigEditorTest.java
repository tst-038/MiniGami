package io.github.tst_038.minigami.paper.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigEditorTest {

    @Test
    void valuesAreReadAsYaml() {
        assertEquals(5, ConfigEditor.parse("5"));
        assertEquals(true, ConfigEditor.parse("true"));
        assertEquals("<gold>Void Fall", ConfigEditor.parse("<gold>Void Fall"));
        assertEquals("in-place", ConfigEditor.parse("\"in-place\""));
        assertEquals(List.of("winter", "summer"), ConfigEditor.parse("[winter, summer]"));
        assertEquals(Map.of("void-kills", Map.of("y", 0)), ConfigEditor.parse("{ void-kills: { y: 0 } }"));
    }

    @Test
    void whatIsNoValueIsRefused() {
        assertNull(ConfigEditor.parse("[unclosed"));
        assertNull(ConfigEditor.parse("a: b"));
        assertNull(ConfigEditor.parse("# only a comment"));
        // A comment would quietly cut the text short; in quotes the # is kept.
        assertNull(ConfigEditor.parse("Night #1"));
        assertEquals("Night #1", ConfigEditor.parse("\"Night #1\""));
    }

    @Test
    void aTextMayStartWithAColourCode() {
        assertEquals("&c&lYuletide Crate", ConfigEditor.parse("&c&lYuletide Crate"));
    }

    @Test
    void aTypedNameLosesItsQuotesAndKeepsTheRest() {
        assertEquals("<gold>Game Night: Finals", ConfigEditor.text("\"<gold>Game Night: Finals\""));
        assertEquals("Game Night: Finals", ConfigEditor.text("Game Night: Finals"));
        assertEquals("Night #1", ConfigEditor.text("Night #1"));
        assertEquals("2026", ConfigEditor.text("2026"));
        assertEquals("&cCrate", ConfigEditor.text("&cCrate"));
    }

    @Test
    void variantsAndTheirKeysComeFromTheGamesKeys() {
        var keys = List.of("gametype", "name", "lives", "features.playing.decay", "variants", "variants.duos", "variants.duos.name",
                "variants.lives");
        assertEquals(List.of("duos", "lives"), ConfigEditor.variantIds(keys));
        assertEquals(List.of("features.playing.decay", "lives", "name"), ConfigEditor.variantKeys(keys));
    }
}
