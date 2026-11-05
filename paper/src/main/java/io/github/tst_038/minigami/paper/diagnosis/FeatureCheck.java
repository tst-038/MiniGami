package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;

/** The checks the game's features bring along, such as terrain that doesn't fit the arena's regions. */
final class FeatureCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        hosting.features().forEach(feature -> feature.diagnose(hosting.arena(), diagnosis));
    }
}
