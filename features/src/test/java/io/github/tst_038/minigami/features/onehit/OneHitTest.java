package io.github.tst_038.minigami.features.onehit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import java.util.Map;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

class OneHitTest {

    private final FakeMatch match = new FakeMatch();
    private final Player hider = FakeMatch.proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID()));
    private final Player seeker = FakeMatch.proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID()));

    OneHitTest() {
        match.join(hider);
        match.join(seeker);
        new OneHit().enable(match);
    }

    @SuppressWarnings("removal") // the one constructor that takes a damage source without a server
    private boolean hit(Entity by) {
        var source = FakeMatch.proxy(DamageSource.class, Map.of("getCausingEntity", by));
        return match.fire(new EntityDamageByEntityEvent(by, hider, DamageCause.ENTITY_ATTACK, source, 2)).isCancelled();
    }

    private boolean hiderIsIn() {
        return match.roster.find(hider.getUniqueId()).orElseThrow().isAlive();
    }

    @Test
    void aPlayersHitPutsThemOutWithoutHurting() {
        assertTrue(hit(seeker));
        assertFalse(hiderIsIn());
    }

    @Test
    void otherHitsAndOwnShotsHurtAsUsual() {
        assertFalse(hit(FakeMatch.proxy(Zombie.class, Map.of("getUniqueId", UUID.randomUUID()))));
        assertFalse(hit(hider));
        assertTrue(hiderIsIn());
    }
}
