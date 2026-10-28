package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Takes everyone past each build in turn; players score it from 1 to 5 with the items in their
 * hotbar. Each vote counts as points for the builder; after the last build the phase ends and the
 * match's win condition ranks the builders.
 */
final class Voting implements Feature {

    /** The item for a score of 1, 2, … 5. */
    private static final List<Material> SCORES = List.of(Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.GREEN_TERRACOTTA);

    private final Plots plots;
    private final long ticksPerBuild;

    Voting(Plots plots, long ticksPerBuild) {
        this.plots = plots;
        this.ticksPerBuild = ticksPerBuild;
    }

    @Override
    public void enable(MatchContext context) {
        // Builds of those who left are skipped.
        var builders = plots.builders().stream()
                .filter(id -> context.roster().find(id).filter(Participant::isAlive).isPresent()).toList();
        if (builders.isEmpty()) {
            context.endPhase();
            return;
        }
        var votes = new Votes();
        var shown = new int[1];
        voters(context).forEach(player -> {
            player.setAllowFlight(true);
            for (int score = 1; score <= SCORES.size(); score++) {
                var item = new ItemStack(SCORES.get(score - 1));
                var name = context.messages().render("build-battle.vote-item", Formatter.number("score", score));
                item.editMeta(meta -> meta.itemName(name));
                player.getInventory().setItem(score + 1, item);
            }
        });
        context.scope().register(() -> voters(context).forEach(player -> {
            player.getInventory().clear();
            player.setAllowFlight(false);
        }));
        show(context, builders.getFirst());
        context.scope().register(context.clock().repeat(ticksPerBuild, () -> {
            if (++shown[0] < builders.size()) {
                show(context, builders.get(shown[0]));
            } else {
                announceTotals(context, builders);
                context.endPhase();
            }
        }));

        context.on(PlayerInteractEvent.class, event -> {
            var score = event.getItem() == null ? 0 : SCORES.indexOf(event.getItem().getType()) + 1;
            if (score == 0 || event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            var voter = event.getPlayer();
            var builder = builders.get(shown[0]);
            if (voter.getUniqueId().equals(builder)) {
                context.messages().send(voter, "build-battle.own-build");
                return;
            }
            context.addPoints(builder, votes.vote(builder, voter.getUniqueId(), score));
            context.messages().send(voter, "build-battle.voted", Formatter.number("score", score));
        });
        context.on(PlayerDropItemEvent.class, event -> event.setCancelled(true));
    }

    private static List<Player> voters(MatchContext context) {
        return context.players();
    }

    private void show(MatchContext context, UUID builder) {
        plots.center(builder).ifPresent(center -> voters(context).forEach(player -> player.teleport(center)));
        context.messages().send(context.audience(), "build-battle.showing", Placeholder.component("player", name(context, builder)));
    }

    private static void announceTotals(MatchContext context, List<UUID> builders) {
        builders.stream().sorted(Comparator.comparingInt((UUID builder) -> context.scores().of(builder)).reversed())
                .forEach(builder -> context.messages().send(context.audience(), "build-battle.score",
                        Placeholder.component("player", name(context, builder)), Formatter.number("points", context.scores().of(builder))));
    }

    private static Component name(MatchContext context, UUID id) {
        return context.player(id).map(Player::name).orElse(Component.text("?"));
    }
}
