package io.github.tst_038.minigami.paper.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.loot.ItemCatalog;
import io.github.tst_038.minigami.api.loot.LootPoint;
import io.github.tst_038.minigami.api.loot.LootTables;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import io.github.tst_038.minigami.core.diagnosis.Finding;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class HostingChecksTest {

    /** A game type that needs a hill region, a region of group wall, and a quarter per team. */
    private static final GameType NEEDY = new GameType() {
        @Override
        public String id() {
            return "needy";
        }

        @Override
        public List<Phase> phases() {
            return List.of(Phase.untimed("playing"));
        }

        @Override
        public Map<String, List<Feature>> features() {
            return Map.of();
        }

        @Override
        public WinCondition winCondition() {
            return new LastManStanding();
        }

        @Override
        public List<String> regions() {
            return List.of("hill");
        }

        @Override
        public List<String> regionGroups() {
            return List.of("wall");
        }

        @Override
        public Optional<String> regionPerTeam() {
            return Optional.of("quarter");
        }
    };

    private static Arena arena(int spawns, List<String> regions, boolean map) {
        return new Arena("example", null, IntStream.range(0, spawns).mapToObj(i -> new Arena.Spawn(new Location(null, i, 64, 0), Optional.empty())).toList(),
                Optional.empty(), Optional.empty(), regions.stream().collect(Collectors.toMap(id -> id, HostingChecksTest::region)),
                map ? Optional.of(region("map")) : Optional.empty());
    }

    private static Region region(String id) {
        return new Region(id, null, 0, 64, 0, 4, 64, 4);
    }

    private static List<Finding> check(Check<Hosting> check, Hosting hosting) {
        return Diagnosis.run("needy", "example", hosting, List.of(check)).findings();
    }

    private static final List<String> ALL_REGIONS = List.of("hill", "wall-north", "quarter-1", "quarter-2");

    @Test
    void anArenaWithEverythingHasNoFindings() {
        var hosting = new Hosting(arena(2, ALL_REGIONS, true), NEEDY, 2, true, true);

        assertEquals(List.of(), Diagnosis.run("needy", "example", hosting, Hosting.checks(true, () -> io.github.tst_038.minigami.api.loot.LootTables.NONE)).findings());
    }

    @Test
    void anArenaWithoutSpawnsCanHostAGameThatNeedsNone() {
        assertEquals(List.of(), check(new SpawnsCheck(), new Hosting(arena(0, ALL_REGIONS, true), NEEDY, 2, false, false, List.of(), false)));
    }

    @Test
    void anArenaWithoutSpawnsCantHost() {
        var findings = check(new SpawnsCheck(), new Hosting(arena(0, ALL_REGIONS, true), NEEDY, 2, false, false));

        assertEquals(List.of("no-spawns"), findings.stream().map(Finding::code).toList());
        assertTrue(findings.getFirst().isBlocking());
        assertEquals(Optional.of("/mg arena example spawn add"), findings.getFirst().fix());
    }

    @Test
    void eachMissingRegionIsAFindingWithItsSetCommand() {
        var findings = check(new RegionsCheck(), new Hosting(arena(1, List.of(), true), NEEDY, 1, false, false));

        assertEquals(Map.of("region", "hill"), findings.getFirst().values());
        assertEquals(Optional.of("/mg arena example region hill set"), findings.getFirst().fix());
    }

    @Test
    void aRegionItsFeaturesRelyOnIsAFindingToo() {
        var hosting = new Hosting(arena(1, List.of("hill"), true), NEEDY, 1, false, false, List.of("fighting", "hill"));

        assertEquals(List.of(Map.of("region", "fighting")), check(new RegionsCheck(), hosting).stream().map(Finding::values).toList());
    }

    @Test
    void aRegionGroupIsMetByAnyRegionInIt() {
        assertEquals(List.of(), check(new RegionGroupsCheck(), new Hosting(arena(1, List.of("wall-north"), true), NEEDY, 1, false, false)));

        var findings = check(new RegionGroupsCheck(), new Hosting(arena(1, List.of("walls"), true), NEEDY, 1, false, false));
        assertEquals(Map.of("group", "wall"), findings.getFirst().values());
        assertEquals(Optional.of("/mg arena example region wall-1 set"), findings.getFirst().fix());
    }

    @Test
    void everyTeamAMatchCanHaveNeedsItsRegion() {
        var findings = check(new TeamRegionsCheck(), new Hosting(arena(4, List.of("quarter-1", "quarter-2"), true), NEEDY, 4, true, false));

        assertEquals(List.of("quarter-3", "quarter-4"), findings.stream().map(finding -> finding.values().get("region")).toList());
        assertEquals(Optional.of("/mg arena example region quarter-3 set"), findings.getFirst().fix());
    }

    @Test
    void changingBlocksNeedsASavedMap() {
        assertEquals(List.of(), check(new MapCheck(), new Hosting(arena(1, List.of(), false), NEEDY, 1, false, false)));
        assertEquals(List.of("no-map"), check(new MapCheck(), new Hosting(arena(1, List.of(), false), NEEDY, 1, false, true)).stream().map(Finding::code).toList());
    }

    @Test
    void everyRegionTheGameNamesMustLieInsideTheSavedMap() {
        var regions = Map.of("hill", region("hill"), "wall-north", region("wall-north"), "quarter-1", region("quarter-1"),
                "quarter-2", new Region("quarter-2", null, 0, 64, 0, 9, 64, 4), "fighting", new Region("fighting", null, 0, 64, 0, 4, 70, 4));
        Arena inMap = new Arena("example", null, List.of(), Optional.empty(), Optional.empty(), regions, Optional.of(new Region("map", null, 0, 60, 0, 5, 66, 5)));

        var findings = check(new MapCoversCheck(), new Hosting(inMap, NEEDY, 2, true, true, List.of("fighting", "hill")));

        assertEquals(List.of("fighting", "quarter-2"), findings.stream().map(finding -> finding.values().get("region")).toList());
        assertTrue(findings.getFirst().isBlocking());
        assertEquals(Optional.of("/mg arena example map save"), findings.getFirst().fix());
        // Without block changes, or without a map (another finding), there is nothing to compare.
        assertEquals(List.of(), check(new MapCoversCheck(), new Hosting(inMap, NEEDY, 2, true, false)));
        assertEquals(List.of(), check(new MapCoversCheck(), new Hosting(arena(1, ALL_REGIONS, false), NEEDY, 2, true, true)));
    }

    @Test
    void changingBlocksNeedsWorldEdit() {
        var hosting = new Hosting(arena(1, List.of(), true), NEEDY, 1, false, true);

        assertEquals(List.of(), check(new WorldEditCheck(true), hosting));
        var findings = check(new WorldEditCheck(false), hosting);
        assertEquals(List.of("no-worldedit"), findings.stream().map(Finding::code).toList());
        assertEquals(Optional.empty(), findings.getFirst().fix());
    }

    @Test
    void fewerSpawnsThanTeamsIsOnlyAWarning() {
        var findings = check(new TeamSpawnsCheck(), new Hosting(arena(2, List.of(), true), NEEDY, 4, true, false));

        assertEquals(List.of("few-team-spawns"), findings.stream().map(Finding::code).toList());
        assertEquals(Map.of("spawns", "2", "teams", "4"), findings.getFirst().values());
        assertTrue(Diagnosis.run("needy", "example", new Hosting(arena(2, List.of(), true), NEEDY, 4, true, false), List.of(new TeamSpawnsCheck())).isReady());
        // Without teams set, or without spawns (another finding), there is nothing to warn about.
        assertEquals(List.of(), check(new TeamSpawnsCheck(), new Hosting(arena(2, List.of(), true), NEEDY, 4, false, false)));
        assertEquals(List.of(), check(new TeamSpawnsCheck(), new Hosting(arena(0, List.of(), true), NEEDY, 4, true, false)));
    }

    private static Hosting looting(List<LootPoint> points) {
        var arena = new Arena("example", null, List.of(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty(), points);
        return new Hosting(arena, NEEDY, 1, false, false, List.of(), false, true);
    }

    private static LootCheck lootCheck(boolean isContainer) throws Exception {
        var files = java.nio.file.Files.createTempDirectory("loot");
        java.nio.file.Files.writeString(files.resolve("rare.yml"), "entries: [stick]");
        java.nio.file.Files.createDirectories(files.resolve("ice"));
        java.nio.file.Files.writeString(files.resolve("ice/only.yml"), "entries: [stick]");
        var catalog = new ItemCatalog(material -> true, material -> 64, name -> false, name -> false, name -> false);
        var tables = LootTables.load(YamlFiles.readTree(files), catalog).tables();
        return new LootCheck(() -> tables, point -> isContainer);
    }

    @Test
    void aLootGameWithoutLootPointsGetsAWarning() throws Exception {
        var findings = check(lootCheck(true), looting(List.of()));

        assertEquals(List.of("no-loot-points"), findings.stream().map(Finding::code).toList());
        assertTrue(Diagnosis.run("needy", "example", looting(List.of()), List.of(lootCheck(true))).isReady());
    }

    @Test
    void aGameWithoutLootIsNotChecked() throws Exception {
        var arena = new Arena("example", null, List.of(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty(), List.of());

        assertEquals(List.of(), check(lootCheck(true), new Hosting(arena, NEEDY, 1, false, false)));
    }

    @Test
    void aPointThatIsNoLongerAContainerGetsAWarning() throws Exception {
        var findings = check(lootCheck(false), looting(List.of(new LootPoint(null, 1, 2, 3, Map.of("rare", 1), 100))));

        assertEquals(List.of("loot-point-gone"), findings.stream().map(Finding::code).toList());
        assertEquals(Map.of("x", "1", "y", "2", "z", "3"), findings.getFirst().values());
    }

    @Test
    void aPointWithAnUnknownTableBlocks() throws Exception {
        var hosting = looting(List.of(new LootPoint(null, 1, 2, 3, Map.of("rare", 1, "missing", 1, "only", 1), 100)));

        var findings = check(lootCheck(true), hosting);

        assertEquals(List.of("unknown-loot-table"), findings.stream().map(Finding::code).toList());
        assertEquals("missing", findings.getFirst().values().get("table"));
        assertTrue(findings.getFirst().isBlocking());
    }
}
