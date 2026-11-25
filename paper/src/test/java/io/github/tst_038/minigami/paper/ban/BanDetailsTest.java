package io.github.tst_038.minigami.paper.ban;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class BanDetailsTest {

    private static final Predicate<String> SCOPES = Set.of("all", "nights", "game:spleef", "koth")::contains;

    @Test
    void nothingTypedIsABanFromAllForGood() {
        assertEquals(Optional.of(new BanDetails("all", Optional.empty(), Optional.empty())), BanDetails.parse("  ", SCOPES));
    }

    @Test
    void scopeLengthAndReason() {
        assertEquals(Optional.of(new BanDetails("game:spleef", Optional.of(Duration.ofDays(7)), Optional.of("griefing the arena"))),
                BanDetails.parse("game:spleef 7d griefing the arena", SCOPES));
    }

    @Test
    void aScopeWithoutLengthTakesTheRestAsReason() {
        assertEquals(Optional.of(new BanDetails("koth", Optional.empty(), Optional.of("spawn camping"))), BanDetails.parse("koth spawn camping", SCOPES));
    }

    @Test
    void aLengthFirstBansFromAll() {
        assertEquals(Optional.of(new BanDetails("all", Optional.of(Duration.ofMinutes(90)), Optional.of("cool off"))),
                BanDetails.parse("1h30m cool off", SCOPES));
    }

    @Test
    void anUnknownFirstWordIsRefused() {
        assertEquals(Optional.empty(), BanDetails.parse("spawn camping", SCOPES));
        assertEquals("spawn", BanDetails.firstWord(" spawn camping"));
    }
}
