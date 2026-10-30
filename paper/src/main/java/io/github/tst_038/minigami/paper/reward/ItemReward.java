package io.github.tst_038.minigami.paper.reward;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Items put in the winner's inventory; what doesn't fit is dropped at their feet.
 *
 * @param givenName as written; empty shows the amount and the item in the player's language
 */
public record ItemReward(Material material, int amount, Optional<String> givenName) implements Reward {

    private static final int MOST = 64 * 36;

    @Override
    public String name() {
        return givenName.orElseGet(() -> amount + "x <lang:" + material.translationKey() + ">");
    }

    @Override
    public void give(Player player) {
        player.getInventory().addItem(new ItemStack(material, amount)).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    /** {@code item: <item>}, with an {@code amount} (1 if left out) and a {@code name} (the amount and item if left out). */
    static final class Type implements RewardType {

        @Override
        public String key() {
            return "item";
        }

        @Override
        public Reward read(ConfigReader entry) {
            var given = entry.string("item");
            var material = given.isEmpty() ? null : Material.matchMaterial(given);
            if (!given.isEmpty() && material == null) {
                entry.problem("item", "unknown item '" + given + "'");
            }
            int amount = entry.has("amount") ? entry.integer("amount", 1, MOST) : 1;
            var name = entry.has("name") ? Optional.of(entry.string("name")) : Optional.<String>empty();
            return new ItemReward(material == null ? Material.STONE : material, amount, name);
        }
    }
}
