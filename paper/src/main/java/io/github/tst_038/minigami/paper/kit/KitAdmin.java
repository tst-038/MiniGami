package io.github.tst_038.minigami.paper.kit;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.features.kit.SavedKit;
import java.io.IOException;
import java.util.Collection;
import java.util.TreeMap;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** What the {@code /mg kit} commands do. Call the public methods from the server thread. */
public final class KitAdmin {

    /** Hotbar and main inventory, boots to helmet, and offhand: the slots a player inventory addresses by index. */
    private static final int SLOTS = 41;

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
        for (int slot = 0; slot < SLOTS; slot++) {
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
