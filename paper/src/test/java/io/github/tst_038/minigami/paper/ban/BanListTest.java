package io.github.tst_038.minigami.paper.ban;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.ban.Ban;
import io.github.tst_038.minigami.api.ban.MiniGamiBans;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BanListTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BanList list = new BanList();

    private Ban ban(UUID player, String scope, Optional<Duration> length) {
        return new Ban(player, player == ann ? "Ann" : "Bob", scope, length.map(NOW::plus), Optional.empty(), "Staff", NOW);
    }

    @Test
    void aBanCoversItsOwnScopeOnly() {
        var spleef = ban(ann, MiniGamiBans.game("spleef"), Optional.empty());
        list.put(spleef);

        assertEquals(Optional.of(spleef), list.find(ann, List.of("game:spleef"), NOW));
        assertEquals(Optional.empty(), list.find(ann, List.of("game:paint", MiniGamiBans.NIGHTS), NOW));
        assertEquals(Optional.empty(), list.find(bob, List.of("game:spleef"), NOW));
    }

    @Test
    void aBanFromAllCoversEveryScope() {
        list.put(ban(ann, MiniGamiBans.ALL, Optional.empty()));

        assertTrue(list.find(ann, List.of("game:spleef"), NOW).isPresent());
        assertTrue(list.find(ann, List.of(MiniGamiBans.NIGHTS), NOW).isPresent());
        assertTrue(list.find(ann, Set.of("koth"), NOW).isPresent());
    }

    @Test
    void anEndedBanNoLongerCountsNorShows() {
        list.put(ban(ann, MiniGamiBans.NIGHTS, Optional.of(Duration.ofHours(1))));

        assertTrue(list.find(ann, List.of(MiniGamiBans.NIGHTS), NOW.plus(Duration.ofMinutes(59))).isPresent());
        assertFalse(list.find(ann, List.of(MiniGamiBans.NIGHTS), NOW.plus(Duration.ofHours(1))).isPresent());
        assertEquals(List.of(), list.active(NOW.plus(Duration.ofHours(2))));
    }

    @Test
    void theLongestLastingBanIsFound() {
        list.put(ban(ann, "game:spleef", Optional.of(Duration.ofDays(7))));
        var forever = ban(ann, MiniGamiBans.ALL, Optional.empty());
        list.put(forever);
        list.put(ban(ann, MiniGamiBans.NIGHTS, Optional.of(Duration.ofDays(30))));

        assertEquals(Optional.of(forever), list.find(ann, List.of("game:spleef", MiniGamiBans.NIGHTS), NOW));
    }

    @Test
    void banningAgainInAScopeReplacesTheBan() {
        list.put(ban(ann, MiniGamiBans.NIGHTS, Optional.of(Duration.ofDays(1))));
        var longer = ban(ann, MiniGamiBans.NIGHTS, Optional.of(Duration.ofDays(9)));
        list.put(longer);

        assertEquals(List.of(longer), list.active(NOW));
    }

    @Test
    void unbanningTakesOutOneScopeOrAll() {
        var nights = ban(ann, MiniGamiBans.NIGHTS, Optional.empty());
        var spleef = ban(ann, "game:spleef", Optional.empty());
        list.put(nights);
        list.put(spleef);
        list.put(ban(bob, MiniGamiBans.NIGHTS, Optional.empty()));

        assertEquals(List.of(nights), list.remove(ann, Optional.of(MiniGamiBans.NIGHTS)));
        assertEquals(List.of(), list.remove(ann, Optional.of(MiniGamiBans.NIGHTS)));
        assertEquals(List.of(spleef), list.remove(ann, Optional.empty()));
        assertEquals(1, list.active(NOW).size());
    }

    @Test
    void aPlayerIsFoundByTheirBannedNameIgnoringCase() {
        list.put(ban(bob, MiniGamiBans.ALL, Optional.empty()));

        assertEquals(Optional.of(bob), list.named("bOB"));
        assertEquals(Optional.empty(), list.named("Ann"));
    }
}
