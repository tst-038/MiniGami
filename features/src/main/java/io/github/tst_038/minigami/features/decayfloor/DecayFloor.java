package io.github.tst_038.minigami.features.decayfloor;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.features.footprint.Footprint;
import java.util.LinkedHashMap;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Breaks each of the listed blocks a short while after a player first steps on it, so players have
 * to keep moving; the match puts them back when it ends. Every block under their feet counts, so
 * walking along an edge doesn't help.
 */
public final class DecayFloor implements Feature {

    private final long delayTicks;
    private final Set<Material> blocks;

    public DecayFloor(long delayTicks, Set<Material> blocks) {
        this.delayTicks = delayTicks;
        this.blocks = Set.copyOf(blocks);
    }

    @Override
    public void enable(MatchContext context) {
        // When each stepped-on block breaks; one repeating task instead of one per block, so the match owns it all.
        var due = new LinkedHashMap<Block, Long>();
        context.scope().register(context.clock().repeat(1, () -> {
            var now = context.clock().elapsedTicks();
            context.roster().alive().stream().map(Participant::id).flatMap(id -> context.player(id).stream())
                    .flatMap(player -> Footprint.under(player.getLocation()).stream())
                    .filter(block -> blocks.contains(block.getType()))
                    .forEach(block -> due.putIfAbsent(block, now + delayTicks));
            due.entrySet().removeIf(entry -> {
                if (entry.getValue() > now) {
                    return false;
                }
                var block = entry.getKey();
                // Something else may have broken it in the meantime.
                if (blocks.contains(block.getType())) {
                    context.rememberOriginal(block);
                    block.setType(Material.AIR);
                }
                return true;
            });
        }));
    }
}
