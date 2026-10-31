package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;

/** At least one region of each group the game uses, such as {@code wall} or {@code wall-north}. */
final class RegionGroupsCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        hosting.gametype().regionGroups().stream()
                .filter(group -> hosting.arena().regions().values().stream().noneMatch(region -> region.isIn(group)))
                .forEach(group -> diagnosis.blocking("missing-region-group", Map.of("group", group), hosting.command("region " + group + "-1 set")));
    }
}
