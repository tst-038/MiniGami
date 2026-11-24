package io.github.tst_038.minigami.api.loot;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * The items saved with {@code /mga loot save <name>}, for other plugins that name them in their own
 * configuration, such as in a {@code saved:} reward. Get it from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(MiniGamiSavedItems.class)} (null while MiniGami isn't enabled).
 * Items can be saved, replaced and deleted while the server runs, so look one up again when you need it
 * rather than keeping it. The items are there from the moment MiniGami is enabled, and change when {@code /mg reload} reads them again.
 * Call it on the server thread.
 */
public interface MiniGamiSavedItems {

    /** A copy of the saved item, so nobody changes the saved one; empty if there is no item of that name. */
    Optional<ItemStack> find(String name);

    /** Whether there is an item of that name. */
    default boolean has(String name) {
        return find(name).isPresent();
    }
}
