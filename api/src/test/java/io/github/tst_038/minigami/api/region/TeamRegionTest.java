package io.github.tst_038.minigami.api.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.core.roster.Roster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamRegionTest {

    private final Roster roster = new Roster();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final Map<String, Region> regions = new HashMap<>();

    TeamRegionTest() {
        List.of(a, b, c).forEach(roster::add);
        for (int i = 1; i <= 3; i++) {
            regions.put("plot-" + i, new Region("plot-" + i, null, i * 10, 0, 0, i * 10 + 5, 5, 5));
        }
    }

    private List<TeamRegion> assign() {
        return TeamRegion.assign("plot", roster.teams(), name -> Optional.ofNullable(regions.get(name)));
    }

    @Test
    void everyTeamGetsTheNextRegionInTeamOrder() {
        roster.assignTeam(a, "red");
        roster.assignTeam(b, "blue");
        roster.assignTeam(c, "red");

        assertEquals(List.of(
                new TeamRegion(regions.get("plot-1"), Optional.of("red"), List.of(a, c)),
                new TeamRegion(regions.get("plot-2"), Optional.of("blue"), List.of(b))), assign());
    }

    @Test
    void aPlayerWithoutATeamGetsARegionOfTheirOwn() {
        roster.assignTeam(a, "red");

        assertEquals(List.of(
                new TeamRegion(regions.get("plot-1"), Optional.of("red"), List.of(a)),
                new TeamRegion(regions.get("plot-2"), Optional.empty(), List.of(b)),
                new TeamRegion(regions.get("plot-3"), Optional.empty(), List.of(c))), assign());
    }

    @Test
    void anArenaWithTooFewRegionsIsAnError() {
        regions.remove("plot-3");

        assertThrows(IllegalStateException.class, this::assign);
    }
}
