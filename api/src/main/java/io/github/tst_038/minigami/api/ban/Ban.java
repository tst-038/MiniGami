package io.github.tst_038.minigami.api.ban;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A player kept out of a scope, such as {@code all}, {@code nights}, {@code game:spleef} or a plugin's own.
 *
 * @param name the player's name when they were banned, to show and to unban them by while offline
 * @param end when the ban is over; empty for a ban that never ends
 * @param by who banned them: a player's name, or "Console"
 * @param at when they were banned
 */
public record Ban(UUID player, String name, String scope, Optional<Instant> end, Optional<String> reason, String by, Instant at) {

    /** Whether the ban is over at {@code now}. */
    public boolean isOver(Instant now) {
        return end.isPresent() && !end.get().isAfter(now);
    }

    /** Whether the ban keeps the player out of {@code scope}: it is for that scope or for {@link MiniGamiBans#ALL}. */
    public boolean covers(String scope) {
        return this.scope.equals(MiniGamiBans.ALL) || this.scope.equals(scope);
    }

    /** How long the ban still lasts at {@code now}; empty for one that never ends. */
    public Optional<Duration> left(Instant now) {
        return end.map(time -> Duration.between(now, time));
    }
}
