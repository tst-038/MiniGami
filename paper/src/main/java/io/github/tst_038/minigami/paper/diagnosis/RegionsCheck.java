package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;
import java.util.stream.Stream;

/** The regions the game's code and features rely on, such as a hill, each one its own finding. */
final class RegionsCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        Stream.concat(hosting.gametype().regions().stream(), hosting.featureRegions().stream()).distinct().filter(region -> !hosting.arena().regions().containsKey(region) && !(region.equals("map") && hosting.arena().map().isPresent()))
                .forEach(region -> diagnosis.blocking("missing-region", Map.of("region", region), hosting.command("region " + region + " set")));
    }
}
