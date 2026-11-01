package io.github.tst_038.minigami.paper.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
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

        assertEquals(List.of(), Diagnosis.run("needy", "example", hosting, Hosting.checks(true)).findings());
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
}
