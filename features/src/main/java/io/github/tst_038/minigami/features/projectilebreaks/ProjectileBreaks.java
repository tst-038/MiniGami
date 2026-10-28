package io.github.tst_038.minigami.features.projectilebreaks;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.event.entity.ProjectileHitEvent;

/** Projectiles that players throw or shoot break the listed blocks they hit; the match puts them back when it ends. */
public final class ProjectileBreaks implements Feature {

    private final Set<Material> blocks;

    public ProjectileBreaks(Set<Material> blocks) {
        this.blocks = Set.copyOf(blocks);
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(ProjectileHitEvent.class, event -> {
            var block = event.getHitBlock();
            if (block != null && blocks.contains(block.getType())) {
                block.setType(Material.AIR);
            }
        });
    }
}
