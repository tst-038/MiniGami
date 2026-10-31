package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;

/** A game that changes blocks needs the arena's saved map, the only thing that puts them back. */
final class MapCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        if (hosting.changesBlocks() && hosting.arena().map().isEmpty()) {
            diagnosis.blocking("no-map", Map.of(), hosting.command("map save"));
        }
    }
}
