package io.github.tst_038.minigami.core.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PresenceTest {

    private final UUID player = UUID.randomUUID();
    private final List<String> log = new ArrayList<>();
    private final Presence presence = new Presence(id -> log.add("enter"), id -> log.add("leave"));

    @Test
    void entersOnFirstClaimAndLeavesOnLastRelease() {
        var claim = presence.claim(player);
        assertTrue(presence.contains(player));
        claim.close();
        assertFalse(presence.contains(player));
        assertEquals(List.of("enter", "leave"), log);
    }

    @Test
    void overlappingClaimsEnterAndLeaveOnce() {
        // A match inside a game night's hold, or an in-place wait followed by saved state.
        var night = presence.claim(player);
        var match = presence.claim(player);
        night.close();
        assertTrue(presence.contains(player));
        match.close();
        assertEquals(List.of("enter", "leave"), log);
    }

    @Test
    void closingTwiceReleasesOnce() {
        var first = presence.claim(player);
        var second = presence.claim(player);
        first.close();
        first.close();
        assertTrue(presence.contains(player));
        second.close();
        assertEquals(List.of("enter", "leave"), log);
    }

    @Test
    void claimingAgainAfterLeavingIsANewPair() {
        presence.claim(player).close();
        presence.claim(player).close();
        assertEquals(List.of("enter", "leave", "enter", "leave"), log);
    }

    @Test
    void callbacksSeeTheStateAfterTheChange() {
        var seen = new ArrayList<Boolean>();
        var holder = new Presence[1];
        holder[0] = new Presence(id -> seen.add(holder[0].contains(id)), id -> seen.add(holder[0].contains(id)));
        holder[0].claim(player).close();
        assertEquals(List.of(true, false), seen);
    }

    @Test
    void playersAreIndependent() {
        presence.claim(player);
        assertFalse(presence.contains(UUID.randomUUID()));
    }
}
