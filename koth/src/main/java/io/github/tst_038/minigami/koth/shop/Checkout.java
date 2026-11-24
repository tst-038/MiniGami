package io.github.tst_038.minigami.koth.shop;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import java.util.HashSet;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Sells an entry to a player: takes the points first, atomically, then gives the rewards, and gives the points
 * back if the rewards can't be delivered. So points are never taken without a delivery, and nothing is
 * delivered without points. A player has one purchase in flight at a time. Server thread only.
 */
public final class Checkout {

    private final Messages messages;
    private final Wallet wallet;
    private final MiniGamiPlayers players;
    private final Executor serverThread;
    private final Logger logger;
    private final Set<UUID> buying = new HashSet<>();

    public Checkout(Messages messages, Wallet wallet, MiniGamiPlayers players, Executor serverThread, Logger logger) {
        this.messages = messages;
        this.wallet = wallet;
        this.players = players;
        this.serverThread = serverThread;
        this.logger = logger;
    }

    public void buy(Player player, ShopEntry entry) {
        var id = player.getUniqueId();
        if (players.isInMatch(id)) {
            messages.send(player, "shop.in-match");
        } else if (entry.ownedPermission().filter(player::hasPermission).isPresent()) {
            messages.send(player, "shop.owned");
        } else if (!buying.add(id)) {
            messages.send(player, "shop.busy");
        } else {
            wallet.spend(id, entry.cost()).handleAsync((left, failure) -> {
                buying.remove(id);
                if (failure != null) {
                    logger.log(Level.SEVERE, "Could not take " + entry.cost() + " points from " + player.getName() + " for " + entry.id(), failure);
                    messages.send(player, "shop.failed");
                } else {
                    settle(player, entry, left);
                }
                return null;
            }, serverThread);
        }
    }

    /** The points are taken when {@code left} is present: deliver, or give them back. */
    private void settle(Player player, ShopEntry entry, OptionalLong left) {
        if (left.isEmpty()) {
            wallet.balance(player.getUniqueId()).thenAccept(points -> messages.send(player, "shop.too-poor",
                    Placeholder.unparsed("cost", String.valueOf(entry.cost())), Placeholder.unparsed("points", String.valueOf(points))));
        } else if (!player.isOnline() || players.isInMatch(player.getUniqueId())) {
            // Items given to someone who has gone, or whom MiniGami holds, would be lost.
            refund(player, entry, "they were no longer free to receive it");
        } else {
            try {
                entry.rewards().forEach(reward -> reward.give(player));
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not give " + entry.id() + " to " + player.getName(), e);
                refund(player, entry, "giving it failed");
                messages.send(player, "shop.failed");
                return;
            }
            logger.info(player.getName() + " bought " + entry.id() + " for " + entry.cost() + " points");
            messages.send(player, "shop.bought", Placeholder.component("entry", messages.parse(entry.name())),
                    Placeholder.unparsed("cost", String.valueOf(entry.cost())));
        }
    }

    private void refund(Player player, ShopEntry entry, String why) {
        logger.warning("Giving " + player.getName() + " their " + entry.cost() + " points back for " + entry.id() + ": " + why);
        wallet.credit(player.getUniqueId(), entry.cost());
    }
}
