package io.github.tst_038.minigami.api.menu;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * A click in a {@link Menu}.
 *
 * @param place the content slot that was clicked; empty for the centre of the bar
 */
public record MenuClick(Player player, ClickType type, Menu menu, Optional<Place> place) {
}
