package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;

/** With fewer spawns than teams, teams start mixed at a shared spawn; the game still plays. */
final class TeamSpawnsCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        var spawns = hosting.arena().spawns().size();
        // No spawns at all is the spawns check's finding.
        if (hosting.hasTeams() && spawns > 0 && spawns < hosting.mostTeams()) {
            diagnosis.warning("few-team-spawns", Map.of("spawns", String.valueOf(spawns), "teams", String.valueOf(hosting.mostTeams())),
                    hosting.command("spawn add"));
        }
    }
}
