package io.github.tst_038.minigami.paper.info;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigInfoTest {

    @Test
    void showsValuesAsInlineYaml() {
        var location = new LinkedHashMap<String, Object>();
        location.put("world", "world");
        location.put("x", 1.23456);
        location.put("y", 64);
        assertEquals("{world: world, x: 1.23, y: 64}", ConfigInfo.show(location));
        assertEquals("[Rocket, Tree]", ConfigInfo.show(List.of("Rocket", "Tree")));
        assertEquals("{y: 0}", ConfigInfo.show(Map.of("y", 0)));
        assertEquals("true", ConfigInfo.show(true));
    }
}
