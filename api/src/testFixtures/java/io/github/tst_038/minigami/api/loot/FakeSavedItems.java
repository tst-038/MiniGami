package io.github.tst_038.minigami.api.loot;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

/**
 * Saved items by name only: making an item needs a server, so {@link #find} is always empty while {@link #has} knows the
 * names, which is all that reading config asks.
 */
public final class FakeSavedItems implements MiniGamiSavedItems {

    private final Set<String> names;

    public FakeSavedItems(String... names) {
        this.names = new HashSet<>(Set.of(names));
    }

    @Override
    public Optional<ItemStack> find(String name) {
        return Optional.empty();
    }

    @Override
    public boolean has(String name) {
        return names.contains(name);
    }

    @Override
    public CompletableFuture<Void> save(String name, ItemStack item) {
        names.add(name);
        return CompletableFuture.completedFuture(null);
    }
}
