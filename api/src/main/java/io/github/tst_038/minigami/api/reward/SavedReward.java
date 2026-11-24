package io.github.tst_038.minigami.api.reward;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * An item saved with {@code /mga loot save}, put in the winner's inventory like an {@link ItemReward}: for
 * what {@code item} can't describe, such as a crate key or an item with its own name and enchantments. It is
 * looked up when it is given, as loot does, so changing the saved item changes what is won; one that has since
 * been deleted gives nothing.
 *
 * @param saved where the item is looked up by {@code item}
 * @param givenName as written; empty shows the amount and the saved name
 */
record SavedReward(String item, int amount, Optional<String> givenName, MiniGamiSavedItems saved) implements Reward {

    @Override
    public String name() {
        return givenName.orElseGet(() -> amount + "x " + item);
    }

    @Override
    public void give(Player player) {
        saved.find(item).ifPresentOrElse(
                found -> player.getInventory().addItem(found.asQuantity(amount)).values()
                        .forEach(left -> player.getWorld().dropItem(player.getLocation(), left)),
                () -> player.getServer().getLogger().warning("The saved item '" + item + "' is gone, so " + player.getName() + " got no reward"));
    }

    /** {@code saved: <name>}, with an {@code amount} (1 if left out) and a {@code name} (the amount and saved name if left out). */
    static final class Type implements RewardType {

        private final MiniGamiSavedItems saved;

        Type(MiniGamiSavedItems saved) {
            this.saved = saved;
        }

        @Override
        public String key() {
            return "saved";
        }

        @Override
        public Reward read(ConfigReader entry) {
            var item = entry.string("saved");
            if (!item.isEmpty() && !saved.has(item)) {
                entry.problem("saved", "no saved item '" + item + "'; save the item you hold with /mga loot save " + item);
            }
            int amount = entry.has("amount") ? entry.integer("amount", 1, ItemReward.MOST) : 1;
            var name = entry.has("name") ? Optional.of(entry.string("name")) : Optional.<String>empty();
            return new SavedReward(item, amount, name, saved);
        }
    }
}
