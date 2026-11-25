package io.github.tst_038.minigami.paper.ban;

import io.github.tst_038.minigami.api.ban.Ban;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The bans in memory, at most one per player and scope. Safe to use from any thread. */
final class BanList {

    /** Of two bans, the one that lasts longer: one that never ends, else the later end. */
    private static final Comparator<Ban> LASTING = Comparator.comparing(ban -> ban.end().orElse(Instant.MAX));

    private final Map<UUID, Map<String, Ban>> bans = new HashMap<>();

    synchronized void put(Ban ban) {
        bans.computeIfAbsent(ban.player(), player -> new LinkedHashMap<>()).put(ban.scope(), ban);
    }

    /** Takes out the player's ban in the scope, or all of theirs if it is empty; returns those taken out, ended ones too. */
    synchronized List<Ban> remove(UUID player, Optional<String> scope) {
        var own = bans.getOrDefault(player, Map.of());
        var removed = scope.isEmpty() ? List.copyOf(own.values()) : Optional.ofNullable(own.get(scope.get())).stream().toList();
        if (!removed.isEmpty()) {
            if (scope.isEmpty()) {
                bans.remove(player);
            } else {
                own.remove(scope.get());
            }
        }
        return removed;
    }

    /** The longest-lasting ban of the player at {@code now} that covers any of the scopes. */
    synchronized Optional<Ban> find(UUID player, Collection<String> scopes, Instant now) {
        return bans.getOrDefault(player, Map.of()).values().stream()
                .filter(ban -> !ban.isOver(now) && scopes.stream().anyMatch(ban::covers)).max(LASTING);
    }

    /** The bans that have not ended at {@code now}. */
    synchronized List<Ban> active(Instant now) {
        var active = new ArrayList<Ban>();
        bans.values().forEach(own -> own.values().stream().filter(ban -> !ban.isOver(now)).forEach(active::add));
        return active;
    }

    /** The player with a ban, ended or not, under this name, ignoring case. */
    synchronized Optional<UUID> named(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        return bans.values().stream().flatMap(own -> own.values().stream())
                .filter(ban -> ban.name().toLowerCase(Locale.ROOT).equals(lower)).map(Ban::player).findFirst();
    }
}
