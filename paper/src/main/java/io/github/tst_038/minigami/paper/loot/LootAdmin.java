package io.github.tst_038.minigami.paper.loot;

import io.github.tst_038.minigami.api.loot.ItemSpec;
import io.github.tst_038.minigami.api.loot.LootTables;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.loot.Entry;
import io.github.tst_038.minigami.core.range.IntRange;
import io.github.tst_038.minigami.core.loot.TableLookup;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** What the {@code /mg loot} commands do. Call the public methods from the server thread. */
public final class LootAdmin {

    private final Plugin plugin;
    private final Supplier<LootTables> tables;
    private final SavedItems saved;
    private final Messages messages;

    public LootAdmin(Plugin plugin, Supplier<LootTables> tables, SavedItems saved, Messages messages) {
        this.plugin = plugin;
        this.tables = tables;
        this.saved = saved;
        this.messages = messages;
    }

    public Collection<String> tableIds() {
        return tables.get().ids();
    }

    public Collection<String> savedNames() {
        return saved.names();
    }

    public void list(CommandSender sender) {
        var known = tables.get();
        if (known.ids().isEmpty()) {
            messages.send(sender, "loot.none");
        } else {
            messages.send(sender, "loot.header", Formatter.number("count", known.ids().size()), Placeholder.unparsed("styles", String.join(", ", known.styles())));
            known.ids().forEach(id -> messages.send(sender, "loot.entry", Placeholder.unparsed("table", id)));
        }
        if (!saved.names().isEmpty()) {
            messages.send(sender, "loot.saved-header", Formatter.number("count", saved.names().size()),
                    Placeholder.unparsed("names", String.join(", ", saved.names())));
        }
    }

    /** What the table can give and how likely each entry is, a pool at a time. */
    public void info(CommandSender sender, String id) {
        tables.get().find(id).ifPresentOrElse(table -> {
            messages.send(sender, "loot.info", Placeholder.unparsed("table", id), Formatter.number("pools", table.pools().size()));
            for (var pool : table.pools()) {
                messages.send(sender, "loot.pool", Placeholder.unparsed("rolls", range(pool.rolls())), Placeholder.unparsed("chance", percent(pool.chance())));
                int total = pool.entries().stream().mapToInt(Entry::weight).sum();
                for (var entry : pool.entries()) {
                    messages.send(sender, "loot.pool-entry", Placeholder.unparsed("chance", percent(100.0 * entry.weight() / total)),
                            Placeholder.unparsed("what", describe(entry)));
                }
            }
        }, () -> messages.send(sender, "loot.unknown", Placeholder.unparsed("table", id)));
    }

    /**
     * Rolls the table once and lists what came out. A table of a style, {@code christmas/rare}, rolls with that style's tables
     * for the ones it nests.
     */
    public void roll(CommandSender sender, String id) {
        var known = tables.get();
        var table = known.find(id);
        if (table.isEmpty()) {
            messages.send(sender, "loot.unknown", Placeholder.unparsed("table", id));
            return;
        }
        int slash = id.indexOf('/');
        TableLookup<ItemSpec> lookup = slash < 0 ? known : known.inStyle(id.substring(0, slash));
        var given = table.get().roll(ThreadLocalRandom.current(), lookup);
        if (given.isEmpty()) {
            messages.send(sender, "loot.rolled-nothing", Placeholder.unparsed("table", id));
            return;
        }
        messages.send(sender, "loot.rolled", Placeholder.unparsed("table", id), Formatter.number("count", given.size()));
        given.forEach(spec -> messages.send(sender, "loot.pool-entry", Placeholder.unparsed("chance", ""), Placeholder.unparsed("what", describe(spec))));
    }

    /** Saves the held item, one of it, under the name, replacing one of that name. */
    public void save(Player player, String name) {
        if (!SavedItems.isValidName(name)) {
            messages.send(player, "loot.invalid-name", Placeholder.unparsed("name", name));
            return;
        }
        var held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            messages.send(player, "loot.hold-an-item");
            return;
        }
        var text = SavedItems.toText(held);
        saved.put(name, held);
        var tags = new TagResolver[] { Placeholder.unparsed("name", name) };
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                saved.write(name, text);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the saved item " + name, e);
                messages.send(player, "loot.save-failed", tags);
                return;
            }
            messages.send(player, "loot.saved", tags);
        });
    }

    /** A table that uses the item keeps working until the next reload, which then reports it. */
    public void delete(CommandSender sender, String name) {
        if (!saved.remove(name)) {
            messages.send(sender, "loot.unknown-item", Placeholder.unparsed("name", name));
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                saved.delete(name);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not delete the saved item " + name, e);
                messages.send(sender, "loot.delete-failed", Placeholder.unparsed("name", name));
                return;
            }
            messages.send(sender, "loot.deleted", Placeholder.unparsed("name", name));
        });
    }

    private static String describe(Entry<ItemSpec> entry) {
        return switch (entry) {
            case Entry.Item<ItemSpec> item -> describe(item.item());
            case Entry.Table<ItemSpec> table -> "table " + table.id();
            case Entry.Nothing<ItemSpec> nothing -> "nothing";
        };
    }

    private static String describe(ItemSpec spec) {
        return switch (spec) {
            case ItemSpec.Saved named -> range(named.amount()) + " x saved item " + named.name();
            case ItemSpec.Vanilla item -> {
                var text = new StringJoiner(" ");
                text.add(range(item.amount()) + " x " + item.material().getKey().getKey());
                item.name().ifPresent(name -> text.add("named " + name));
                item.enchants().forEach((name, level) -> text.add(name + " " + range(level)));
                item.potion().ifPresent(potion -> text.add(potion));
                yield text.toString();
            }
        };
    }

    private static String range(IntRange range) {
        return range.min() == range.max() ? String.valueOf(range.min()) : range.min() + "-" + range.max();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f%%" : "%.1f%%", value);
    }
}
