package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;

/** Pasting the saved map back after a game that changes blocks needs WorldEdit. */
final class WorldEditCheck implements Check<Hosting> {

    private final boolean installed;

    /** @param installed whether WorldEdit or FastAsyncWorldEdit is on the server */
    WorldEditCheck(boolean installed) {
        this.installed = installed;
    }

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        if (hosting.changesBlocks() && !installed) {
            diagnosis.blocking("no-worldedit", Map.of());
        }
    }
}
