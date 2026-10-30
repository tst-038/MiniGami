package io.github.tst_038.minigami.features.nomobs;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.Set;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Players can't make mobs appear in the arena: no spawn eggs (also from dispensers), mob buckets,
 * thrown eggs, breeding or building golems and withers. Natural spawns and the rest of the world are
 * left alone, and so are armor stands, which builders use.
 */
public final class NoMobs implements Feature {

    private static final Set<SpawnReason> BY_PLAYERS = Set.of(SpawnReason.SPAWNER_EGG, SpawnReason.DISPENSE_EGG, SpawnReason.EGG,
            SpawnReason.BUCKET, SpawnReason.BREEDING, SpawnReason.BUILD_IRONGOLEM, SpawnReason.BUILD_SNOWMAN, SpawnReason.BUILD_WITHER);

    @Override
    public void enable(MatchContext context) {
        context.on(CreatureSpawnEvent.class, event -> {
            if (BY_PLAYERS.contains(event.getSpawnReason())) {
                event.setCancelled(true);
            }
        });
    }
}
