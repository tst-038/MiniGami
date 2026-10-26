package io.github.tst_038.minigami.core.phase;

/** One stage of a match, such as waiting, playing or ending. A timed phase ends on its own. */
public record Phase(String id, long durationTicks) {

    public Phase {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Phase id must not be blank");
        }
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Duration must not be negative, got " + durationTicks);
        }
    }

    /** A phase that lasts until something advances the match, for example a win. */
    public static Phase untimed(String id) {
        return new Phase(id, 0);
    }

    /** A phase that advances on its own after {@code durationTicks}, unless advanced earlier. */
    public static Phase timed(String id, long durationTicks) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("A timed phase needs at least 1 tick, got " + durationTicks);
        }
        return new Phase(id, durationTicks);
    }

    public boolean isTimed() {
        return durationTicks > 0;
    }
}
