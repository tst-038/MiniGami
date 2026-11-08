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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Picks the match's target items when play starts and tells everyone, then repeats them on the action bar.
 * A player who right clicks a block of a {@code deposit} region while holding a target still to hand in gives
 * one in, which scores a point for their team (a team never scores the same item twice); {@link FirstDeposit}
 * decides when that wins. Any block of the region is a deposit, so the arena draws its deposits as regions
 * around whatever it builds there. The HUD gets {@code %item%} for a single target, else {@code %item1%} and
 * on, each ticked off for the viewer's team once handed in.
 */
final class Deposit implements Feature {

    /** The regions whose blocks take the target item; an arena has any number. */
    static final String REGION = "deposit";

    private final ItemPool pool;
    private final int targets;

    Deposit(ItemPool pool, int targets) {
        this.pool = pool;
        this.targets = targets;
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
        context.on(PlayerInteractEvent.class, event -> {
            var block = event.getClickedBlock();
            if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                    || deposits.stream().noneMatch(region -> region.contains(block.getLocation()))) {
                return;
            }
            event.setCancelled(true);
            var player = event.getPlayer();
            var id = player.getUniqueId();
            var inventory = player.getInventory();
            var held = inventory.getItemInMainHand();
            boolean alive = context.roster().find(id).filter(Participant::isAlive).isPresent();
            var wanted = new HashSet<>(chosen);
            wanted.removeAll(done.apply(id));
            if (!accepts(wanted, held.getType(), true, alive)) {
                if (alive) {
                    player.sendActionBar(context.messages().render("item-race.wrong", Placeholder.component("item", names(List.copyOf(wanted)))));
                }
                return;
            }
            var material = held.getType();
            held.subtract();
            inventory.setItemInMainHand(held);
            var side = handedIn.computeIfAbsent(sideOf.apply(id), key -> new HashSet<>());
            side.add(material);
            context.messages().send(context.audience(), "item-race.deposited",
                    Placeholder.component("item", Component.translatable(material.translationKey())),
                    Placeholder.component("player", context.playerName(player)),
                    Placeholder.unparsed("done", String.valueOf(side.size())),
                    Placeholder.unparsed("total", String.valueOf(chosen.size())));
            context.addPoints(id, 1);
        });
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
