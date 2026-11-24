package io.github.tst_038.minigami.koth.hill;

import java.util.Optional;
import java.util.UUID;

/**
 * What one second on the hill brought about.
 *
 * @param captured who took the hill this second
 * @param earner who earned points this second
 * @param earned how many points the earner got; 0 when there is no earner
 */
public record HillTick(Optional<UUID> captured, Optional<UUID> earner, long earned) {

    public static final HillTick NOTHING = new HillTick(Optional.empty(), Optional.empty(), 0);
}
