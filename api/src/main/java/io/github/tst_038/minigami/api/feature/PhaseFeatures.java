package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.scope.Scope;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Enables the features assigned to a phase when that phase starts, each with a context bound to
 * the phase scope, so they are all released when the phase ends.
 */
public final class PhaseFeatures implements PhaseListener {

    private final Map<String, List<Feature>> featuresByPhase;
    private final BiFunction<Phase, Scope, MatchContext> contexts;

    /**
     * @param featuresByPhase features per phase id; phases without an entry have none
     * @param contexts creates the context a phase's features get, bound to that phase's scope
     */
    public PhaseFeatures(Map<String, List<Feature>> featuresByPhase, BiFunction<Phase, Scope, MatchContext> contexts) {
        this.featuresByPhase = Map.copyOf(featuresByPhase);
        this.contexts = contexts;
    }

    /**
     * A failing feature doesn't stop the others; failures are rethrown afterwards as one
     * {@link IllegalStateException}.
     */
    @Override
    public void onEnter(Phase phase, Scope scope) {
        var features = featuresByPhase.getOrDefault(phase.id(), List.of());
        if (features.isEmpty()) {
            return;
        }
        var context = contexts.apply(phase, scope);
        IllegalStateException failure = null;
        for (Feature feature : features) {
            try {
                feature.enable(context);
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = new IllegalStateException("Failed to enable a feature in phase " + phase.id(), e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
