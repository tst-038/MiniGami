package io.github.tst_038.minigami.api.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.core.terrain.HeightDistribution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerrainStylesTest {

    private static final BlockCatalog CATALOG = new BlockCatalog(id -> !id.contains("ghost"));

    private static final String STYLE = """
            shape:
              base: 32
              hills: {amplitude: 16, scale: 48, octaves: 4}
              detail: {amplitude: 2, scale: 12, octaves: 2}
              sea-level: 30
              deep-below: 12
              pad-radius: 5
              rock: stone
              deep-rock: deepslate
              floor: bedrock
            surface:
              top: grass_block
              under: dirt
              depth: 3-5
              shore: sand
              shore-under: sandstone
              lakebed: {gravel: 3, clay: 1}
            caves: {width: 0.08, scale: 20, min-depth: 4, lava-level: 10}
            ores:
              - ores/vanilla
              - {name: emerald, ore: emerald_ore, deep-ore: deepslate_emerald_ore, size: 2, veins: 5, air-discard: 0.5,
                 height: {shape: uniform, min: 4, max: 60}}
            pools: [{name: water-pools, fluid: water, count: 1-3, radius: 2-4, depth: 3}]
            trees: [{name: trees, log: oak_log, leaves: oak_leaves, height: 4-6, count: 10-20, grows-on: [grass_block]}]
            decor: [{name: flowers, blocks: {dandelion: 1, poppy: 2}, density: 0.1, stands-on: [grass_block]}]
            groups: {ores: [diamond_ore, deepslate_diamond_ore]}
            """;

    private static final String ORES = """
            ores:
              - {name: diamond, ore: diamond_ore, deep-ore: deepslate_diamond_ore, size: 8, veins: 4, air-discard: 0.5,
                 height: {shape: triangle, min: 5, max: 30}}
            """;

    @TempDir
    Path directory;

    private TerrainStyles.Loaded load(String... filesAndText) throws Exception {
        var terrain = Files.createDirectories(directory.resolve("terrain"));
        for (int i = 0; i < filesAndText.length; i += 2) {
            var file = terrain.resolve(filesAndText[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, filesAndText[i + 1]);
        }
        return TerrainStyles.load(YamlFiles.readTree(terrain), CATALOG);
    }

    private static List<String> problems(TerrainStyles.Loaded loaded) {
        return loaded.problems().stream().map(Object::toString).toList();
    }

    @Test
    void readsAStyleAndIncludesOreSets() throws Exception {
        var loaded = load("plains.yml", STYLE, "ores/vanilla.yml", ORES);

        assertEquals(List.of(), problems(loaded));
        assertEquals(List.of("plains"), List.copyOf(loaded.styles().ids()));
        var style = loaded.styles().find("plains").orElseThrow();
        assertEquals(List.of("diamond", "emerald"), style.ores().stream().map(ore -> ore.name()).toList());
        assertEquals("minecraft:diamond_ore", style.ores().getFirst().ore());
        assertTrue(style.ores().getFirst().height() instanceof HeightDistribution.Triangle);
        assertEquals("minecraft:stone", style.shape().rock());
        assertEquals(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"), style.groups().get("ores"));
        assertTrue(style.caves().isPresent());
        assertEquals(2, style.decor().getFirst().blocks().size());
    }

    @Test
    void problemsNameTheFileAndKeyAndLeaveTheStyleOut() throws Exception {
        var broken = STYLE.replace("rock: stone", "rock: ghost_stone").replace("- ores/vanilla", "- ores/missing")
                .replace("size: 2", "size: 99").replace("octaves: 4", "octaves: 0");
        var loaded = load("bad.yml", broken, "ok.yml", STYLE.replace("- ores/vanilla", "").replace("- {name: emerald", "- {name: emerald"));

        var text = String.join("\n", problems(loaded));
        assertTrue(text.contains("terrain/bad.yml") && text.contains("shape.rock") && text.contains("unknown block"), text);
        assertTrue(text.contains("no ore set 'ores/missing'"), text);
        assertTrue(text.contains("ores[1].size: must be between 1 and 64"), text);
        assertTrue(text.contains("hills.octaves"), text);
        assertTrue(loaded.styles().find("bad").isEmpty());
        assertTrue(loaded.styles().find("ok").isPresent(), text);
    }

    @Test
    void unknownKeysAreProblems() throws Exception {
        var loaded = load("typo.yml", STYLE.replace("- ores/vanilla", "").replace("caves:", "cavs:"));
        assertTrue(problems(loaded).stream().anyMatch(problem -> problem.contains("cavs")), problems(loaded).toString());
    }
}
