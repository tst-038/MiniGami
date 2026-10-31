package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;

/** The regions the game's code relies on, such as a hill, each one its own finding. */
final class RegionsCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        hosting.gametype().regions().stream().filter(region -> !hosting.arena().regions().containsKey(region))
                .forEach(region -> diagnosis.blocking("missing-region", Map.of("region", region), hosting.command("region " + region + " set")));
    }
}
