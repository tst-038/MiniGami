package io.github.tst_038.minigami.koth.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.tst_038.minigami.koth.ledger.Balances;
import io.github.tst_038.minigami.koth.schedule.Window;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KothPlaceholdersTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 20, 0, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final UUID ALICE = UUID.randomUUID();

    private final Balances balances = new Balances();

    private KothPlaceholders over(KothStatus status) {
        return new KothPlaceholders(() -> status, balances, CLOCK);
    }

    private static KothStatus active() {
        return new KothStatus(Optional.of(new Window(NOW.minusMinutes(5), NOW.plusMinutes(10))), Optional.of(ALICE), Optional.of("Alice"), Optional.empty(), 0,
                Optional.of(NOW.plusHours(2)));
    }

    private static KothStatus idle() {
        return new KothStatus(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.of(NOW.plusMinutes(90)));
    }

    @Test
    void anActiveRoundShowsItsHolderAndTimeLeft() {
        var placeholders = over(active());

        assertEquals("true", placeholders.resolve(Optional.empty(), "active"));
        assertEquals("Alice", placeholders.resolve(Optional.empty(), "holder"));
        assertEquals("10m", placeholders.resolve(Optional.empty(), "time_left"));
        assertEquals("Mon 22:00", placeholders.resolve(Optional.empty(), "next_start"));
        assertEquals("2h", placeholders.resolve(Optional.empty(), "next_in"));
    }

    @Test
    void anIdleKothShowsWhenItStartsNext() {
        var placeholders = over(idle());

        assertEquals("false", placeholders.resolve(Optional.empty(), "active"));
        assertEquals("", placeholders.resolve(Optional.empty(), "holder"));
        assertEquals("", placeholders.resolve(Optional.empty(), "time_left"));
        assertEquals("Mon 21:30", placeholders.resolve(Optional.empty(), "next_start"));
        assertEquals("1h 30m", placeholders.resolve(Optional.empty(), "next_in"));
    }

    @Test
    void withNothingPlannedTheTextsAreEmpty() {
        var placeholders = over(KothStatus.OFF);

        assertEquals("false", placeholders.resolve(Optional.empty(), "active"));
        assertEquals("", placeholders.resolve(Optional.empty(), "next_start"));
        assertEquals("", placeholders.resolve(Optional.empty(), "next_in"));
    }

    @Test
    void theBalanceComesFromTheLoadedBalances() {
        var placeholders = over(KothStatus.OFF);
        assertEquals("", placeholders.resolve(Optional.of(ALICE), "balance"));
        assertEquals("", placeholders.resolve(Optional.empty(), "balance"));

        balances.put(ALICE, 42);

        assertEquals("42", placeholders.resolve(Optional.of(ALICE), "balance"));
    }

    @Test
    void anUnknownNameGivesNull() {
        assertNull(over(active()).resolve(Optional.empty(), "nonsense"));
    }
}
