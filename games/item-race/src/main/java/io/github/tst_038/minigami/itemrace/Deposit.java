package io.github.tst_038.minigami.itemrace;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Picks the match's one target item when play starts and tells everyone, then repeats it on the action
 * bar. A player who right clicks a block of a {@code deposit} region while holding the target hands one
 * in, which scores a point and so wins the match for their team ({@link FirstDeposit}). Any block of the
 * region is a deposit, so the arena draws its deposits as regions around whatever it builds there.
 */
final class Deposit implements Feature {

    /** The regions whose blocks take the target item; an arena has any number. */
    static final String REGION = "deposit";

    private final ItemPool pool;

    Deposit(ItemPool pool) {
        this.pool = pool;
    }

    /** Whether a click hands the target in: a player still in, holding it, on a deposit. */
    static boolean accepts(Material target, Material held, boolean onDeposit, boolean alive) {
        return alive && onDeposit && held == target;
    }

    @Override
    public List<String> regionGroups() {
        return List.of(REGION);
    }

    @Override
    public void enable(MatchContext context) {
        var target = pool.pick(new Random(context.random().nextLong()));
        var item = Placeholder.component("item", Component.translatable(target.translationKey()));
        var deposits = context.regions(REGION);
        context.messages().send(context.audience(), "item-race.target", item);
        context.scope().register(context.clock().repeat(20, () -> context.audience().sendActionBar(context.messages().render("item-race.target-bar", item))));
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
            if (!accepts(target, held.getType(), true, alive)) {
                if (alive) {
                    player.sendActionBar(context.messages().render("item-race.wrong", item));
                }
                return;
            }
            held.subtract();
            inventory.setItemInMainHand(held);
            context.messages().send(context.audience(), "item-race.deposited", item, Placeholder.component("player", context.playerName(player)));
            context.addPoints(id, 1);
        });
    }
}
