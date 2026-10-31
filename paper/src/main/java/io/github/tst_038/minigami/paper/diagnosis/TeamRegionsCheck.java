package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;
import java.util.stream.IntStream;

/** A region for every team a match can have, such as {@code quarter-1} up to {@code quarter-4}, each missing one its own finding. */
final class TeamRegionsCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        hosting.gametype().regionPerTeam().ifPresent(prefix -> IntStream.rangeClosed(1, hosting.mostTeams())
                .mapToObj(number -> prefix + "-" + number)
                .filter(region -> !hosting.arena().regions().containsKey(region))
                .forEach(region -> diagnosis.blocking("missing-team-region",
                        Map.of("region", region, "teams", String.valueOf(hosting.mostTeams())), hosting.command("region " + region + " set"))));
    }
}
