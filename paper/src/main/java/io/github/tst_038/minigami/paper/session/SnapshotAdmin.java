package io.github.tst_038.minigami.paper.session;

import io.github.tst_038.minigami.api.message.Messages;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Lets staff look at a player's saved state and put it back by hand. The snapshot still to be
 * restored comes first; without one, the one restored last is used.
 *
 * <p>Call the public methods from the server thread.
 */
public final class SnapshotAdmin {

    private final Plugin plugin;
    private final SnapshotStore store;
    private final PlayerSessions sessions;
    private final Messages messages;

    public SnapshotAdmin(Plugin plugin, SnapshotStore store, PlayerSessions sessions, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.sessions = sessions;
        this.messages = messages;
    }

    public void inspect(CommandSender sender, Player target) {
        withSnapshot(sender, target, (snapshot, saved) -> {
            var player = Placeholder.component("player", target.name());
            messages.send(sender, saved ? "snapshot.saved" : "snapshot.last", player);
            var location = snapshot.location();
            messages.send(sender, "snapshot.details",
                    Placeholder.unparsed("game-mode", snapshot.gameMode().toLowerCase()),
                    Placeholder.unparsed("world", Optional.ofNullable(plugin.getServer().getWorld(location.world()))
                            .map(World::getName).orElse(location.world().toString())),
                    Placeholder.unparsed("x", "%.1f".formatted(location.x())),
                    Placeholder.unparsed("y", "%.1f".formatted(location.y())),
                    Placeholder.unparsed("z", "%.1f".formatted(location.z())),
                    Placeholder.unparsed("health", "%.1f".formatted(snapshot.health())),
                    Formatter.number("food", snapshot.foodLevel()),
                    Formatter.number("level", snapshot.level()));
            // displayName() carries the item as hover, so staff can see enchantments and names.
            var items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(snapshot.items()));
            messages.send(sender, "snapshot.items", Placeholder.component("items", Component.join(JoinConfiguration.commas(true),
                    Arrays.stream(items).filter(Objects::nonNull).filter(item -> !item.isEmpty())
                            .map(item -> Component.text(item.getAmount() + "x ").append(item.displayName())).toList())));
            messages.send(sender, "snapshot.effects", Placeholder.unparsed("effects",
                    String.join(", ", snapshot.effects().stream().map(PlayerSnapshot.SavedEffect::type).toList())));
            messages.send(sender, saved ? "snapshot.restore-hint" : "snapshot.restore-last-hint", player);
        });
    }

    public void restore(CommandSender sender, Player target) {
        withSnapshot(sender, target, (snapshot, saved) -> {
            var player = Placeholder.component("player", target.name());
            try {
                if (saved) {
                    sessions.restoreSaved(target, snapshot);
                } else {
                    sessions.restoreLast(target, snapshot);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not restore the state of " + target.getName(), e);
                messages.send(sender, "snapshot.restore-failed", player);
                return;
            }
            plugin.getLogger().info(sender.getName() + " restored the " + (saved ? "saved" : "last") + " state of " + target.getName());
            messages.send(sender, saved ? "snapshot.restored" : "snapshot.restored-last", player);
        });
    }

    /**
     * Reads the snapshot off the server thread, then hands it over on the target's thread with whether
     * it is the one still to be restored. Players in a match are left alone: their state comes back
     * when they leave it.
     */
    private void withSnapshot(CommandSender sender, Player target, BiConsumer<PlayerSnapshot, Boolean> action) {
        var id = target.getUniqueId();
        var player = Placeholder.component("player", target.name());
        if (sessions.isInMatch(id)) {
            messages.send(sender, "snapshot.in-match", player);
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            Optional<PlayerSnapshot> saved;
            Optional<PlayerSnapshot> last;
            try {
                saved = store.load(id);
                last = saved.isPresent() ? Optional.empty() : store.loadLast(id);
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the saved state of " + target.getName(), e);
                messages.send(sender, "snapshot.unreadable", player);
                return;
            }
            target.getScheduler().run(plugin, ignored -> {
                // The player may have joined a match while the file was read.
                if (sessions.isInMatch(id)) {
                    messages.send(sender, "snapshot.in-match", player);
                } else if (saved.isPresent()) {
                    action.accept(saved.get(), true);
                } else if (last.isPresent()) {
                    action.accept(last.get(), false);
                } else {
                    messages.send(sender, "snapshot.none", player);
                }
            }, null);
        });
    }
}
