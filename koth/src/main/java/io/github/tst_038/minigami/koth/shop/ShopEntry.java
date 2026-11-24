package io.github.tst_038.minigami.koth.shop;

import io.github.tst_038.minigami.api.reward.Reward;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;

/**
 * One thing the shop sells.
 *
 * @param slot where its icon sits in the menu
 * @param cost in points, at least 1
 * @param name the icon's name, as MiniMessage text; also what the buyer is told they bought
 * @param lore the icon's extra lines, as MiniMessage text
 * @param rewards all of them are given on a purchase
 * @param ownedPermission whoever has it already can't buy the entry again
 */
public record ShopEntry(String id, int slot, int cost, Material icon, String name, List<String> lore, List<Reward> rewards, Optional<String> ownedPermission) {

    public ShopEntry {
        lore = List.copyOf(lore);
        rewards = List.copyOf(rewards);
    }
}
