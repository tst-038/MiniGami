package io.github.tst_038.minigami.api.feature;

/**
 * One reusable piece of game behaviour, such as void kills or no hunger, active during the phases
 * a game assigns it to.
 *
 * <p>There is no {@code disable()}: everything a feature sets up goes through its
 * {@link MatchContext}, and is released automatically when the phase ends.
 */
public interface Feature {

    /** Called when one of the feature's phases starts. Subscribe to events and schedule tasks here. */
    void enable(MatchContext context);
}
