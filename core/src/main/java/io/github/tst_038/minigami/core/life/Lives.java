package io.github.tst_038.minigami.core.life;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Deaths per participant, against the lives each has. Lives are asked at every death, so a
 * participant who changes role is held to the new role's lives.
 */
public final class Lives {

    private final ToIntFunction<UUID> livesOf;
    private final Map<UUID, Integer> deaths = new HashMap<>();

    /** @param livesOf the lives a participant has; 0 for unlimited */
    public Lives(ToIntFunction<UUID> livesOf) {
        this.livesOf = livesOf;
    }

    /**
     * Counts a death.
     *
     * @return whether it cost the participant their last life
     */
    public boolean die(UUID id) {
        var lives = livesOf.applyAsInt(id);
        return lives > 0 && deaths.merge(id, 1, Integer::sum) >= lives;
    }

    /** The lives the participant has left; empty when they have unlimited lives. */
    public OptionalInt left(UUID id) {
        var lives = livesOf.applyAsInt(id);
        return lives == 0 ? OptionalInt.empty() : OptionalInt.of(Math.max(0, lives - deaths.getOrDefault(id, 0)));
    }

    /** Forgets the participant's deaths, so they start over with full lives. */
    public void reset(UUID id) {
        deaths.remove(id);
    }
}
