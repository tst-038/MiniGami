package io.github.tst_038.minigami.paper.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.terrain.BlockCatalog;
import io.github.tst_038.minigami.api.terrain.TerrainStyles;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundledTerrainTest {

    @Test
    void theExampleStylesAreValid() {
        var loaded = TerrainStyles.load(YamlFiles.readTree(Path.of("src/main/resources/terrain")), new BlockCatalog(id -> true));

        assertEquals(List.of(), loaded.problems());
        assertEquals(List.of("badlands", "desert", "forest", "plains", "snowy"), List.copyOf(loaded.styles().ids()));
        assertEquals(7, loaded.styles().find("plains").orElseThrow().ores().size());
    }
}
