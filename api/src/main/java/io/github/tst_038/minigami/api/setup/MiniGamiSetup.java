package io.github.tst_038.minigami.api.setup;

import io.github.tst_038.minigami.api.region.Region;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * The tools MiniGami's own arena commands use to set up regions, for other plugins that keep regions of their own: take the
 * region a player has selected, select one for them, and show where one is. Get it from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(MiniGamiSetup.class)} (null while MiniGami isn't enabled).
 *
 * <p>Selecting needs WorldEdit or FastAsyncWorldEdit, which MiniGami only uses when it is installed. To write a region or
 * a location into your own config, see {@link Region#toConfig()} and
 * {@link io.github.tst_038.minigami.api.config.ConfigLocations#write}.
 *
 * <p>Call it on the server thread.
 */
public interface MiniGamiSetup {

    /** How long {@link #show} outlines a region for. */
    int OUTLINE_SECONDS = 10;

    /** Whether WorldEdit or FastAsyncWorldEdit is installed, which {@link #selection} and {@link #select} need. */
    boolean hasWorldEdit();

    /**
     * The smallest box around the player's WorldEdit selection, in the player's world, as a region of that name.
     *
     * @return empty if there is no WorldEdit or the player has not selected both corners; {@link #hasWorldEdit()} tells which
     */
    Optional<Region> selection(Player player, String id);

    /**
     * Makes the region the player's WorldEdit selection, so they can adjust it with WorldEdit's own tools.
     *
     * @throws IllegalStateException without WorldEdit
     */
    void select(Player player, Region region);

    /**
     * Outlines the region's edges in particles for {@value #OUTLINE_SECONDS} seconds, only for this player, and stops early if
     * they leave the region's world. Needs no WorldEdit.
     */
    void show(Player player, Region region);
}
