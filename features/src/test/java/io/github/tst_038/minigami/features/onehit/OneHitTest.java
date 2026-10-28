package io.github.tst_038.minigami.features.onehit;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    private double hit(Entity by) {
        var source = FakeMatch.proxy(DamageSource.class, Map.of("getCausingEntity", by));
        return match.fire(new EntityDamageByEntityEvent(by, hider, DamageCause.ENTITY_ATTACK, source, 2)).getDamage();
    }

    @Test
    void aPlayersHitKills() {
        assertEquals(1000, hit(seeker));
    }

    @Test
    void otherHitsAndOwnShotsHurtAsUsual() {
        assertEquals(2, hit(FakeMatch.proxy(Zombie.class, Map.of("getUniqueId", UUID.randomUUID()))));
        assertEquals(2, hit(hider));
    }
}
