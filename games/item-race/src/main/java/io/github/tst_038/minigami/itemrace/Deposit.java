package io.github.tst_038.minigami.itemrace;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Picks the match's target items when play starts and tells everyone, then repeats them on the action bar.
 * A target item a player drops that reaches a {@code deposit} region (say the bottom of a hole) gives one in
 * for them (or, in click mode, right clicking a block of a deposit while holding it does), then their team must wait out the delay before
 * its next hand-in counts; which scores a point for their team (a team never scores the same item twice); {@link FirstDeposit}
 * decides when that wins. Any block of the region is a deposit, so the arena draws its deposits as regions
 * around whatever it builds there. The HUD gets {@code %item%} for a single target, else {@code %item1%} and
 * on, each ticked off for the viewer's team once handed in.
 */
final class Deposit implements Feature {

    /** The regions whose blocks take the target item; an arena has any number. */
    static final String REGION = "deposit";

    private final ItemPool pool;
    private final int targets;
    private final HandIn mode;
    private final long delayTicks;

    /** How an item is handed in: dropped so that it reaches a deposit, or right clicked onto one. */
    enum HandIn {
        DROP, CLICK
    }

    /** @param delayTicks how long a team must wait after a hand-in before its next one counts */
    Deposit(ItemPool pool, int targets, HandIn mode, long delayTicks) {
        this.pool = pool;
        this.targets = targets;
        this.mode = mode;
        this.delayTicks = delayTicks;
    }

    /** Whether a click hands an item in: a player still in, holding one of the items still wanted, on a deposit. */
    static boolean accepts(Set<Material> wanted, Material held, boolean onDeposit, boolean alive) {
        return alive && onDeposit && wanted.contains(held);
    }

    @Override
    public List<String> regionGroups() {
        return List.of(REGION);
    }

    @Override
    public void enable(MatchContext context) {
        var chosen = pool.pick(new Random(context.random().nextLong()), targets);
        var handedIn = new HashMap<String, Set<Material>>();
        Function<UUID, String> sideOf = id -> context.roster().find(id).flatMap(Participant::team).orElse(id.toString());
        Function<UUID, Set<Material>> done = id -> handedIn.getOrDefault(sideOf.apply(id), Set.of());
        var all = names(chosen);
        context.showText(chosen.size() == 1 ? "item" : "item1", id -> entry(context, chosen.getFirst(), done.apply(id)));
        for (int i = 1; i < chosen.size(); i++) {
            var material = chosen.get(i);
            context.showText("item" + (i + 1), id -> entry(context, material, done.apply(id)));
        }
        var deposits = context.regions(REGION);
        context.messages().send(context.audience(), "item-race.target", Placeholder.component("item", all));
        context.scope().register(context.clock().repeat(20, () -> context.audience().sendActionBar(context.messages().render("item-race.target-bar", Placeholder.component("item", all)))));
        var readyAt = new HashMap<String, Long>();
        // Hands in one of the held item if it is still wanted; false means it did not count (the player heard why).
        HandInTest handIn = (player, material, quiet) -> {
            var id = player.getUniqueId();
            boolean alive = context.roster().find(id).filter(Participant::isAlive).isPresent();
            var wanted = new HashSet<>(chosen);
            wanted.removeAll(done.apply(id));
            if (!accepts(wanted, material, true, alive)) {
                if (alive && !quiet) {
                    player.sendActionBar(context.messages().render("item-race.wrong", Placeholder.component("item", names(List.copyOf(wanted)))));
                }
                return false;
            }
            var side = sideOf.apply(id);
            long wait = readyAt.getOrDefault(side, 0L) - context.clock().elapsedTicks();
            if (wait > 0) {
                if (!quiet) {
                    player.sendActionBar(context.messages().render("item-race.cooldown", Placeholder.unparsed("seconds", String.valueOf((wait + 19) / 20))));
                }
                return false;
            }
            readyAt.put(side, context.clock().elapsedTicks() + delayTicks);
            var handed = handedIn.computeIfAbsent(side, key -> new HashSet<>());
            handed.add(material);
            context.messages().send(context.audience(), "item-race.deposited",
                    Placeholder.component("item", Component.translatable(material.translationKey())),
                    Placeholder.component("player", context.playerName(player)),
                    Placeholder.unparsed("done", String.valueOf(handed.size())),
                    Placeholder.unparsed("total", String.valueOf(chosen.size())));
            context.addPoints(id, 1);
            context.count(id, ItemRace.ITEMS_HANDED_IN);
            return true;
        };
        if (mode == HandIn.DROP) {
            // Thrown items are followed until they reach a deposit, then count for whoever threw them (the item remembers). One
            // that is not accepted yet, such as during the wait, stays put and counts later.
            var falling = new HashSet<Item>();
            context.on(PlayerDropItemEvent.class, event -> falling.add(event.getItemDrop()));
            // A stack remembers one thrower, so thrown items must not merge or one team would be credited for another's.
            context.on(ItemMergeEvent.class, event -> event.setCancelled(falling.contains(event.getEntity()) || falling.contains(event.getTarget())));
            context.scope().register(context.clock().repeat(5, () -> falling.removeIf(item -> {
                if (!item.isValid()) {
                    return true;
                }
                var thrower = item.getThrower() == null ? null : item.getServer().getPlayer(item.getThrower());
                var stack = item.getItemStack();
                if (thrower == null || !chosen.contains(stack.getType()) || deposits.stream().noneMatch(region -> region.contains(item.getLocation()))
                        || !handIn.test(thrower, stack.getType(), true)) {
                    return false;
                }
                if (stack.getAmount() > 1) {
                    stack.subtract();
                    item.setItemStack(stack);
                    return false;
                }
                item.remove();
                return true;
            })));
            return;
        }
        context.on(PlayerInteractEvent.class, event -> {
            var block = event.getClickedBlock();
            if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                    || deposits.stream().noneMatch(region -> region.contains(block.getLocation()))) {
                return;
            }
            event.setCancelled(true);
            var player = event.getPlayer();
            var held = player.getInventory().getItemInMainHand();
            if (handIn.test(player, held.getType(), false)) {
                held.subtract();
                player.getInventory().setItemInMainHand(held);
            }
        });
    }

    private interface HandInTest {
        boolean test(Player player, Material material, boolean quiet);
    }

    private static Component names(List<Material> materials) {
        return Component.join(JoinConfiguration.commas(true), materials.stream().map(material -> (Component) Component.translatable(material.translationKey())).toList());
    }

    /** One HUD line: the item, ticked off once the viewer's team has handed it in. */
    private static Component entry(MatchContext context, Material material, Set<Material> done) {
        var key = done.contains(material) ? "item-race.entry-done" : "item-race.entry-open";
        return context.messages().render(key, Placeholder.component("item", Component.translatable(material.translationKey())));
    }
}
