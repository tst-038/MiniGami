package io.github.tst_038.minigami.api.reward;

/** A {@link Reward} that could not be given, such as a command that failed or a saved item that has since been deleted. */
public final class RewardFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** @param message what could not be given and why */
    public RewardFailedException(String message) {
        super(message);
    }
}
