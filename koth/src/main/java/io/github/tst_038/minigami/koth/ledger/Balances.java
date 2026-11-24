package io.github.tst_038.minigami.koth.ledger;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The balances last seen in storage, to show without waiting for it. Safe to read from any thread. */
public final class Balances {

    private final Map<UUID, Long> points = new ConcurrentHashMap<>();

    /** @return the player's points, or empty if they haven't been loaded */
    public OptionalLong of(UUID player) {
        var known = points.get(player);
        return known == null ? OptionalLong.empty() : OptionalLong.of(known);
    }

    public void put(UUID player, long balance) {
        points.put(player, balance);
    }

    public void forget(UUID player) {
        points.remove(player);
    }
}
