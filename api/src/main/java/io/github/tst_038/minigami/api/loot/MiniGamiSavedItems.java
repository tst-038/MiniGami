package io.github.tst_038.minigami.api.loot;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

/**
 * The items saved with {@code /mga loot save <name>}, for other plugins that name them in their own
 * configuration, such as in a {@code saved:} reward, or save items of their own. Get it from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(MiniGamiSavedItems.class)} (null while MiniGami isn't enabled).
 * Items can be saved, replaced and deleted while the server runs, so look one up again when you need it
 * rather than keeping it. The items are there from the moment MiniGami is enabled, and change when {@code /mg reload} reads them again.
 * Call it on the server thread.
 */
public interface MiniGamiSavedItems {

    /** Whether items can be saved under the name: lower-case letters, digits, {@code _} and {@code -}. */
    static boolean isValidName(String name) {
        return name.matches("[a-z0-9_-]+");
    }

    /** A copy of the saved item, so nobody changes the saved one; empty if there is no item of that name. */
    Optional<ItemStack> find(String name);

    /** Whether there is an item of that name. */
    default boolean has(String name) {
        return find(name).isPresent();
    }

    /**
     * Saves one of the item under the name, replacing an item of that name, as {@code /mga loot save} does. {@link #find}
     * gives it at once; its file is written off the server thread.
     *
     * @return completes off the server thread once the file is written, or exceptionally with the {@link java.io.IOException}
     *         if it couldn't be; the item stays saved until the next reload or restart either way
     * @throws IllegalArgumentException if the name isn't {@linkplain #isValidName valid} or the item is empty
     */
    CompletableFuture<Void> save(String name, ItemStack item);
}
