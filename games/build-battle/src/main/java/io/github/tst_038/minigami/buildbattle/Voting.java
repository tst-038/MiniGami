package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.api.region.TeamRegion;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.Comparator;
import java.util.List;
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
 * Takes everyone past each plot in turn; players score it from 1 to 5 with the items in their hotbar,
 * but not their own team's. Each vote counts as points for the plot's team; after the last plot the
 * phase ends and the match's win condition ranks the teams.
 */
final class Voting implements Feature {

    /** The item for a score of 1, 2, … 5. */
    private static final List<Material> SCORES = List.of(Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.GREEN_TERRACOTTA);

    private final long ticksPerBuild;

    Voting(long ticksPerBuild) {
        this.ticksPerBuild = ticksPerBuild;
    }

    @Override
    public void enable(MatchContext context) {
        // Builds whose builders all left are skipped.
        var shownPlots = context.teamRegions().stream()
                .filter(plot -> plot.members().stream().anyMatch(id -> context.roster().find(id).filter(Participant::isAlive).isPresent()))
                .toList();
        if (shownPlots.isEmpty()) {
            context.endPhase();
            return;
        }
        var votes = new Votes();
        var shown = new int[1];
        voters(context).forEach(player -> giveVoteItems(context, player));
        context.scope().register(() -> voters(context).forEach(player -> {
            player.getInventory().clear();
            player.setAllowFlight(false);
        }));
        show(context, shownPlots.getFirst());
        context.scope().register(context.clock().repeat(ticksPerBuild, () -> {
            if (++shown[0] < shownPlots.size()) {
                show(context, shownPlots.get(shown[0]));
            } else {
                announceTotals(context, shownPlots);
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
            var plot = shownPlots.get(shown[0]);
            if (plot.members().contains(voter.getUniqueId())) {
                context.messages().send(voter, "build-battle.own-build");
                return;
            }
            // A team scores as one: the win condition adds up its members' points.
            var builder = plot.members().getFirst();
            context.addPoints(builder, votes.vote(builder, voter.getUniqueId(), score));
            context.messages().send(voter, "build-battle.voted", Formatter.number("score", score));
        });
        context.on(PlayerDropItemEvent.class, event -> event.setCancelled(true));
        context.on(MatchRejoinEvent.class, event -> {
            if (context.roster().find(event.getPlayer().getUniqueId()).filter(Participant::isAlive).isPresent()) {
                giveVoteItems(context, event.getPlayer());
                event.getPlayer().teleport(shownPlots.get(shown[0]).region().standingSpot());
            }
        });
    }

    private static void giveVoteItems(MatchContext context, Player player) {
        player.setAllowFlight(true);
        for (int score = 1; score <= SCORES.size(); score++) {
            var item = new ItemStack(SCORES.get(score - 1));
            var name = context.messages().render("build-battle.vote-item", Formatter.number("score", score));
            item.editMeta(meta -> meta.itemName(name));
            player.getInventory().setItem(score + 1, item);
        }
    }

    private static List<Player> voters(MatchContext context) {
        return context.players();
    }

    private void show(MatchContext context, TeamRegion plot) {
        var start = plot.region().standingSpot();
        voters(context).forEach(player -> player.teleport(start));
        context.messages().send(context.audience(), "build-battle.showing", Placeholder.component("player", name(context, plot)));
    }

    private static void announceTotals(MatchContext context, List<TeamRegion> shown) {
        shown.stream().sorted(Comparator.comparingInt((TeamRegion plot) -> points(context, plot)).reversed())
                .forEach(plot -> context.messages().send(context.audience(), "build-battle.score",
                        Placeholder.component("player", name(context, plot)), Formatter.number("points", points(context, plot))));
    }

    private static int points(MatchContext context, TeamRegion plot) {
        return plot.members().stream().mapToInt(context.scores()::of).sum();
    }

    /** The team's name, or for a player without a team their own. */
    private static Component name(MatchContext context, TeamRegion plot) {
        return plot.team().map(context::teamName)
                .orElseGet(() -> context.player(plot.members().getFirst()).map(Player::name).orElse(Component.text("?")));
    }
}
