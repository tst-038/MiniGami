package io.github.tst_038.minigami.paper.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigEditorTest {

    @Test
    void variantsAndTheirKeysComeFromTheGamesKeys() {
        var keys = List.of("gametype", "name", "lives", "features.playing.decay", "variants", "variants.duos", "variants.duos.name",
                "variants.lives");
        assertEquals(List.of("duos", "lives"), ConfigEditor.variantIds(keys));
        assertEquals(List.of("features.playing.decay", "lives", "name"), ConfigEditor.variantKeys(keys));
    }
}
