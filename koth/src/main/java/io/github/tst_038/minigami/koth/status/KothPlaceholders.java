package io.github.tst_038.minigami.koth.status;

import io.github.tst_038.minigami.koth.ledger.Balances;
import io.github.tst_038.minigami.koth.text.Durations;
import io.github.tst_038.minigami.koth.text.Moments;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The values of KOTH for text: {@code holder}, {@code time_left}, {@code next_start} and {@code next_in}
 * for the sidebar and messages, and with {@code active} and {@code balance} for PlaceholderAPI. A value with
 * nothing to say is empty. Safe to use from any thread.
 */
public final class KothPlaceholders {

    private final Supplier<KothStatus> status;
    private final Balances balances;
    private final Clock clock;

    public KothPlaceholders(Supplier<KothStatus> status, Balances balances, Clock clock) {
        this.status = status;
        this.balances = balances;
        this.clock = clock;
    }

    /**
     * @param viewer who asks, if anyone; only {@code balance} needs them
     * @return the text, or null for a name that isn't a value
     */
    public String resolve(Optional<UUID> viewer, String params) {
        var current = status.get();
        return switch (params) {
            case "active" -> String.valueOf(current.running().isPresent());
            case "balance" -> viewer.map(balances::of).filter(balance -> balance.isPresent()).map(balance -> String.valueOf(balance.getAsLong())).orElse("");
            default -> values(current, LocalDateTime.now(clock)).get(params);
        };
    }

    /** The values by name, empty text where there is nothing to say; shared by the sidebar and the messages. */
    public static Map<String, String> values(KothStatus status, LocalDateTime now) {
        return Map.of(
                "holder", status.holderName().orElse(""),
                "time_left", status.running().map(window -> Durations.format(window.left(now))).orElse(""),
                "next_start", status.nextStart().map(Moments::format).orElse(""),
                "next_in", status.nextStart().map(start -> Durations.format(Duration.between(now, start))).orElse(""));
    }
}
