package io.github.tst_038.minigami.paper.hud;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The match values each player in a match last saw on their HUD, such as {@code time} or
 * {@code lives}, as legacy-coloured text, so other plugins can show them through PlaceholderAPI from
 * any thread without touching the match.
 */
public final class MatchValues {

    private final Map<UUID, Map<String, String>> byPlayer = new ConcurrentHashMap<>();

    void put(UUID player, Map<String, String> values) {
        byPlayer.put(player, Map.copyOf(values));
    }

    void remove(UUID player) {
        byPlayer.remove(player);
    }

    /** @return {@code null} if it isn't a value; empty text for a player not in a match. Any thread. */
    public String get(UUID player, String name) {
        var values = byPlayer.get(player);
        if (values == null) {
            return MatchHud.VALUES.contains(name) ? "" : null;
        }
        return values.get(name);
    }
}
