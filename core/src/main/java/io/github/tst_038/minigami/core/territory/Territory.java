package io.github.tst_038.minigami.core.territory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who owns which block position, with a running count per owner, so painting a block and asking how
 * many an owner holds never scan the territory. Positions are packed into a long with {@link #pack}.
 */
public final class Territory {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;

    private final Map<Long, UUID> owners = new HashMap<>();
    private final Map<UUID, Integer> counts = new HashMap<>();

    /**
     * Packs a block position as Minecraft does: 26 bits each for x and z, 12 for y, so negative
     * coordinates and the world's full height fit.
     */
    public static long pack(int x, int y, int z) {
        return ((long) x & mask(XZ_BITS)) << (XZ_BITS + Y_BITS) | ((long) z & mask(XZ_BITS)) << Y_BITS | ((long) y & mask(Y_BITS));
    }

    private static long mask(int bits) {
        return (1L << bits) - 1;
    }

    /**
     * Makes the owner hold the position.
     *
     * @return who held it before, the owner itself if it already did (then nothing changed); empty if nobody did
     */
    public Optional<UUID> paint(long position, UUID owner) {
        var previous = owners.put(position, owner);
        if (previous == null) {
            counts.merge(owner, 1, Integer::sum);
        } else if (!previous.equals(owner)) {
            counts.merge(previous, -1, Integer::sum);
            counts.merge(owner, 1, Integer::sum);
        }
        return Optional.ofNullable(previous);
    }

    /** How many positions the owner holds. */
    public int count(UUID owner) {
        return counts.getOrDefault(owner, 0);
    }

    /** Leaves every position without an owner. */
    public void reset() {
        owners.clear();
        counts.clear();
    }
}
