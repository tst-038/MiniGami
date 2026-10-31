package io.github.tst_038.minigami.core.diagnosis;

/** How much a {@link Finding} stands in the way. */
public enum Severity {
    /** The arena can't host the game until it is fixed, so the game leaves it out. */
    BLOCKING,
    /** The arena hosts the game, but likely not as meant, such as teams sharing a spawn. */
    WARNING
}
