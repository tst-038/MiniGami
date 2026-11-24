package io.github.tst_038.minigami.koth.shop;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.api.reward.Reward;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Sells an entry to a player: takes the points first, atomically, then gives the rewards one by one. If the
 * first can't be given the points go back, so points are never taken without a delivery and nothing is
 * delivered without points. If a later one fails the points stay taken, since part of it is in the player's
 * hands: the log says what was and wasn't given, for staff to settle. A player has one purchase in flight at a
 * time. Server thread only.
 */
public final class Checkout {

    private final Server server;
    private final Messages messages;
    private final Wallet wallet;
    private final MiniGamiPlayers players;
    private final Executor serverThread;
    private final Logger logger;
    private final Set<UUID> buying = new HashSet<>();

    public Checkout(Server server, Messages messages, Wallet wallet, MiniGamiPlayers players, Executor serverThread, Logger logger) {
        this.server = server;
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
            var name = player.getName();
            // The player isn't kept across the storage round trip; settle looks them up again.
            wallet.spend(id, entry.cost()).handleAsync((left, failure) -> {
                buying.remove(id);
                if (failure != null) {
                    logger.log(Level.SEVERE, "Could not take " + entry.cost() + " points from " + name + " for " + entry.id(), failure);
                    tell(id, "shop.failed");
                } else {
                    settle(id, name, entry, left);
                }
                return null;
            }, serverThread).whenComplete((ignored, failure) -> {
                // Only the hop back to the server thread can fail here, as when the plugin is switching off.
                if (failure != null) {
                    logger.log(Level.SEVERE, "Could not finish the purchase of " + entry.id() + " by " + name + " (" + id + "); "
                            + entry.cost() + " points may have been taken without a delivery and need giving back", failure);
                }
            });
        }
    }

    /** The points are taken when {@code left} is present: deliver, or give them back. */
    private void settle(UUID id, String name, ShopEntry entry, OptionalLong left) {
        var buyer = server.getPlayer(id);
        if (left.isEmpty()) {
            wallet.balance(id).thenAccept(points -> tell(id, "shop.too-poor",
                    Placeholder.unparsed("cost", String.valueOf(entry.cost())), Placeholder.unparsed("points", String.valueOf(points))));
        } else if (buyer == null || players.isInMatch(id)) {
            // Items given to someone who has gone, or whom MiniGami holds, would be lost.
            refund(id, name, entry, "they were no longer free to receive it");
        } else {
            var rewards = entry.rewards();
            var delivered = 0;
            try {
                for (var reward : rewards) {
                    reward.give(buyer);
                    delivered++;
                }
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not give " + entry.id() + " to " + name + " for " + entry.cost() + " points: gave "
                        + delivered + " of " + rewards.size() + " rewards " + names(rewards.subList(0, delivered)) + ", failed on "
                        + names(rewards.subList(delivered, rewards.size())), e);
                if (delivered == 0) {
                    refund(id, name, entry, "giving it failed");
                    messages.send(buyer, "shop.failed");
                } else {
                    messages.send(buyer, "shop.partly-failed");
                }
                return;
            }
            logger.info(name + " bought " + entry.id() + " for " + entry.cost() + " points");
            messages.send(buyer, "shop.bought", Placeholder.component("entry", messages.parse(entry.name())),
                    Placeholder.unparsed("cost", String.valueOf(entry.cost())));
        }
    }

    private static String names(List<Reward> rewards) {
        return rewards.stream().map(Reward::name).toList().toString();
    }

    /** Tells the player, if they are still here. */
    private void tell(UUID id, String key, TagResolver... values) {
        var player = server.getPlayer(id);
        if (player != null) {
            messages.send(player, key, values);
        }
    }

    private void refund(UUID id, String name, ShopEntry entry, String why) {
        logger.warning("Giving " + name + " their " + entry.cost() + " points back for " + entry.id() + ": " + why);
        wallet.credit(id, entry.cost());
    }
}
