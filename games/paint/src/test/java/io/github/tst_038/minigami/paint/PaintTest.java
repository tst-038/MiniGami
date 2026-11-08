package io.github.tst_038.minigami.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;

import java.util.List;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PaintTest {

    @Test
    void playersPaintInAdventureModeOnTheCanvasRegionsAndKeepTheirPaintStats() {
        var paint = new Paint();

        assertEquals(GameMode.ADVENTURE, paint.gameMode("playing"));
        assertEquals(List.of("canvas"), paint.regionGroups());
        assertEquals(Set.of("blocks-painted", "blocks-stolen"), Set.of(Brush.PAINTED.id(), Brush.STOLEN.id()));
        assertEquals(Set.of(Brush.PAINTED, Brush.STOLEN), paint.stats());
    }

    @Test
    void theBrushChangesBlocksSoTheArenaMapIsPutBack() {
        var brush = paintFeature();

        assertTrue(brush.changesBlocks());
        assertEquals(List.of("canvas"), brush.regionGroups());
    }

    @Test
    void radiusZeroIsAllowedAndTooBigARadiusIsAConfigProblem() throws Exception {
        var zero = yaml("radius: 0\nhighlight: false\n");
        new Paint().configure(zero);
        assertDoesNotThrow(zero::check);

        var big = yaml("radius: 9\n");
        new Paint().configure(big);
        assertThrows(InvalidConfigException.class, big::check);
    }

    private static ConfigReader yaml(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return ConfigReader.of("paint.yml", yaml);
    }

    private static Brush paintFeature() {
        return (Brush) new Paint().features().get("playing").getFirst();
    }
}
