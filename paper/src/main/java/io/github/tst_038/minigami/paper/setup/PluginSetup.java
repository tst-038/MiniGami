package io.github.tst_038.minigami.paper.setup;

import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.setup.MiniGamiSetup;
import io.github.tst_038.minigami.api.setup.RegionOutline;
import java.util.Optional;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** {@link MiniGamiSetup} for the plugin: WorldEdit is only touched, through {@link WorldEditSelections}, when it is installed. */
public final class PluginSetup implements MiniGamiSetup {

    private final Plugin plugin;
    private final boolean worldEdit;

    public PluginSetup(Plugin plugin) {
        this.plugin = plugin;
        this.worldEdit = plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null;
    }

    @Override
    public boolean hasWorldEdit() {
        return worldEdit;
    }

    @Override
    public Optional<Region> selection(Player player, String id) {
        return worldEdit ? WorldEditSelections.box(player, id) : Optional.empty();
    }

    @Override
    public void select(Player player, Region region) {
        if (!worldEdit) {
            throw new IllegalStateException("Selecting a region needs WorldEdit");
        }
        WorldEditSelections.select(player, region);
    }

    @Override
    public void show(Player player, Region region) {
        var points = RegionOutline.points(region);
        int[] left = {OUTLINE_SECONDS * 2};
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (--left[0] < 0 || !player.getWorld().equals(region.world())) {
                task.cancel();
                return;
            }
            points.forEach(at -> player.spawnParticle(Particle.END_ROD, at[0], at[1], at[2], 1, 0, 0, 0, 0));
        }, null, 1, 10);
    }
}
