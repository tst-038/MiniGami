package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;

/** Players need somewhere to start. */
final class SpawnsCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        if (hosting.needsSpawn() && !hosting.arena().isPlayable()) {
            diagnosis.blocking("no-spawns", Map.of(), hosting.command("spawn add"));
        }
    }
}
