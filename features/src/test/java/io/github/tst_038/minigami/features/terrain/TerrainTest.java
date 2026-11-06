package io.github.tst_038.minigami.features.terrain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.ArenaLayout;
import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.terrain.BlockCatalog;
import io.github.tst_038.minigami.api.terrain.TerrainStyles;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerrainTest {

    private static final String STYLE = """
            shape:
              base: 40
              hills: {amplitude: 10, scale: 40, octaves: 3}
              detail: {amplitude: 2, scale: 12, octaves: 2}
              sea-level: 30
              deep-below: 12
              pad-radius: 5
              rock: stone
              deep-rock: deepslate
              floor: bedrock
            surface: {top: grass_block, under: dirt, depth: 3-5, shore: sand, shore-under: sandstone, lakebed: {gravel: 1}}
            trees: [{name: trees, log: oak_log, leaves: oak_leaves, height: 4-6, count: 10-20, grows-on: [grass_block]}]
            """;

    @TempDir
    Path directory;

    private final World world = FakeMatch.proxy(World.class, Map.of());
    private final FakeMatch match = new FakeMatch();

    private static ConfigReader config(String yaml) throws Exception {
        var section = new YamlConfiguration();
        section.loadFromString(yaml);
        return ConfigReader.of("games/test.yml", section);
    }

    private TerrainFactory factory(List<String> log) throws Exception {
        var folder = Files.createDirectories(directory.resolve("terrain"));
        Files.writeString(folder.resolve("plains.yml"), STYLE);
        var styles = TerrainStyles.load(YamlFiles.readTree(folder), new BlockCatalog(id -> true)).styles();
        return new TerrainFactory(() -> styles, Runnable::run, log::add, Audience::empty);
    }

    private Terrain terrain(String yaml) throws Exception {
        var root = config(yaml);
        var feature = factory(new ArrayList<>()).create(root.section("terrain"));
        assertDoesNotThrow(root::check);
        return (Terrain) feature;
    }

    @Test
    void anUnknownStyleIsAConfigProblem() throws Exception {
        var root = config("terrain: {style: nope, regions: quarter}");
        factory(new ArrayList<>()).create(root.section("terrain"));
        assertThrows(InvalidConfigException.class, root::check);
    }

    @Test
    void aBadQuotaRangeIsAConfigProblem() throws Exception {
        var root = config("terrain: {style: plains, regions: quarter, quota: {trees: lots}}");
        factory(new ArrayList<>()).create(root.section("terrain"));
        assertThrows(InvalidConfigException.class, root::check);
    }

    @Test
    void placesOneGridPerRegionStandingOnTheSpawn() throws Exception {
        var log = new ArrayList<String>();
        var root = config("terrain: {style: plains, regions: quarter, quota: {trees: 5-8}}");
        var feature = factory(log).create(root.section("terrain"));
        match.regions.put("quarter-1", new Region("quarter-1", world, 0, 0, 0, 63, 79, 63));
        match.regions.put("quarter-2", new Region("quarter-2", world, 100, 0, 0, 163, 79, 63));
        match.regions.put("elsewhere", new Region("elsewhere", world, 300, 0, 0, 363, 79, 63));
        match.spawnPoints.add(new Location(world, 20.5, 45, 20.5));

        feature.enable(match);

        assertEquals(List.of("quarter-1", "quarter-2"), match.grids.stream().map(grid -> grid.box().id()).sorted().toList());
        var grid = match.grids.stream().filter(candidate -> candidate.box().id().equals("quarter-1")).findFirst().orElseThrow();
        assertEquals("minecraft:air", grid.palette().block(grid.volume().get(20, 45, 20)));
        assertTrue(log.getFirst().contains("seed"));
    }

    @Test
    void trimsAWorldHighRegionSoTheGroundLandsOnTheSpawn() {
        var tall = new Region("quarter-1", world, 0, -64, 0, 63, 319, 63);
        var spawn = new Location(world, 20.5, 64, 20.5);

        var window = Areas.windows(List.of(tall), List.of(spawn), 40).getFirst();

        assertEquals(23, window.minY());
        assertEquals(319, window.maxY());
        assertEquals(tall, Areas.windows(List.of(tall), List.of(), 40).getFirst());
        var empty = new Region("quarter-2", world, 100, -64, 0, 163, 319, 63);
        assertEquals(23, Areas.windows(List.of(empty), List.of(spawn), 40).getFirst().minY(), "no spawn of its own: the arena's lowest");
    }

    @Test
    void findsTheGroupItNeedsAndChangesBlocks() throws Exception {
        var terrain = terrain("terrain: {style: plains, regions: quarter}");
        assertTrue(terrain.changesBlocks());
        assertFalse(terrain.needsPlayersToBuild());
        assertEquals(List.of("quarter"), terrain.regionGroups());
    }

    @Test
    void diagnosesSpawnsOutOfTheAreaOrBelowTheSea() throws Exception {
        var terrain = terrain("terrain: {style: plains, regions: quarter}");
        var region = new Region("quarter-1", world, 0, 0, 0, 63, 79, 63);
        var spawns = List.of(new Location(world, 30.5, 78, 30.5), new Location(world, 30.5, 20, 30.5));
        var arena = new ArenaLayout() {
            @Override
            public List<Region> regions(String group) {
                return List.of(region);
            }

            @Override
            public List<Location> spawnPoints() {
                return spawns;
            }
        };

        var findings = Diagnosis.run("g", "a", arena, List.of((subject, diagnosis) -> terrain.diagnose(subject, diagnosis))).findings();

        assertEquals(List.of("anchor-outside-area", "anchor-below-sea"), findings.stream().map(finding -> finding.code()).toList());
        assertFalse(findings.isEmpty());
    }
}
