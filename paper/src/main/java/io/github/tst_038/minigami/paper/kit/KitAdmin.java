package io.github.tst_038.minigami.paper.kit;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.features.kit.SavedKit;
import io.github.tst_038.minigami.paper.item.InventoryPreview;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** What the {@code /mga kit} commands do. Call the public methods from the server thread. */
public final class KitAdmin {

    private final Plugin plugin;
    private final SavedKits kits;
    private final Messages messages;

    public KitAdmin(Plugin plugin, SavedKits kits, Messages messages) {
        this.plugin = plugin;
        this.kits = kits;
        this.messages = messages;
    }

    public Collection<String> names() {
        return kits.names();
    }

    /** Saves the player's inventory as the kit, replacing one of that name. */
    public void save(Player player, String name) {
        if (!SavedKits.isValidName(name)) {
            messages.send(player, "kit.invalid-name", Placeholder.unparsed("name", name));
            return;
        }
        var slots = new TreeMap<Integer, ItemStack>();
        for (int slot = 0; slot < InventoryPreview.PLAYER_SLOTS; slot++) {
            var item = player.getInventory().getItem(slot);
            if (item != null && !item.isEmpty()) {
                slots.put(slot, item.clone());
            }
        }
        if (slots.isEmpty()) {
            messages.send(player, "kit.empty");
            return;
        }
        var kit = new SavedKit(slots);
        // Serialized here on the server thread; only the file write happens in the background.
        var text = SavedKits.toText(kit);
        kits.put(name, kit);
        var tags = new TagResolver[] {
                Placeholder.unparsed("name", name), Formatter.number("items", slots.size()) };
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                kits.write(name, text);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the saved kit " + name, e);
                messages.send(player, "kit.save-failed", tags);
                return;
            }
            messages.send(player, "kit.saved", tags);
        });
    }

    public void list(CommandSender sender) {
        if (kits.names().isEmpty()) {
            messages.send(sender, "kit.none");
            return;
        }
        messages.send(sender, "kit.header", Formatter.number("count", kits.names().size()));
        kits.names().forEach(name -> messages.send(sender, "kit.entry", Placeholder.unparsed("name", name),
                Formatter.number("items", kits.find(name).map(kit -> kit.slots().size()).orElse(0))));
    }

    /** Opens a chest, laid out like the inventory, showing what the kit gives. */
    public void preview(Player player, String name) {
        withKit(player, name, kit -> player.openInventory(InventoryPreview.create(
                messages.render("kit.view.title", Placeholder.unparsed("name", name)), contents(kit), List.of())));
    }

    /** Puts the kit in the player's inventory, slot for slot, so it can be changed and saved again. */
    public void give(Player player, String name) {
        withKit(player, name, kit -> {
            // Only into an empty inventory: overwriting the player's own items would lose them.
            if (!player.getInventory().isEmpty()) {
                messages.send(player, "kit.not-empty", Placeholder.unparsed("name", name));
                return;
            }
            player.getInventory().setContents(contents(kit));
            messages.send(player, "kit.given", Placeholder.unparsed("name", name));
        });
    }

    private void withKit(Player player, String name, Consumer<SavedKit> action) {
        kits.find(name).ifPresentOrElse(action,
                () -> messages.send(player, "kit.unknown", Placeholder.unparsed("name", name)));
    }

    /** The kit's items by player inventory index; copies, so the saved kit stays as it is. */
    static ItemStack[] contents(SavedKit kit) {
        var contents = new ItemStack[InventoryPreview.PLAYER_SLOTS];
        kit.slots().forEach((slot, item) -> {
            if (slot >= 0 && slot < contents.length) {
                contents[slot] = item.clone();
            }
        });
        return contents;
    }

    /** A game that uses the kit keeps working until the next reload, which then reports it. */
    public void delete(CommandSender sender, String name) {
        if (!kits.remove(name)) {
            messages.send(sender, "kit.unknown", Placeholder.unparsed("name", name));
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                kits.delete(name);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not delete the saved kit " + name, e);
                messages.send(sender, "kit.delete-failed", Placeholder.unparsed("name", name));
                return;
            }
            messages.send(sender, "kit.deleted", Placeholder.unparsed("name", name));
        });
    }
}
