package io.github.tst_038.minigami.features.nodamage;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/** Players take no damage from the listed causes, such as falls or hits. */
public final class NoDamage implements Feature {

    private final Set<DamageCause> causes;

    public NoDamage(Set<DamageCause> causes) {
        this.causes = causes.isEmpty() ? Set.of() : EnumSet.copyOf(causes);
    }

    @Override
    public void enable(MatchContext context) {
        context.on(EntityDamageEvent.class, event -> {
            if (causes.contains(event.getCause())) {
                event.setCancelled(true);
            }
        });
    }
}
