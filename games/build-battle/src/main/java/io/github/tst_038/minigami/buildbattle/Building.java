package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Every team gets a plot, the arena regions {@code plot-1}, {@code plot-2} and so on in team order, and
 * its builders build there together in creative, on a theme picked at random. A player without a team
 * builds alone. Nothing they do reaches past their plot.
 */
final class Building implements Feature {

    private final Plots plots;
    private final List<String> themes;
    private final long ticks;

    Building(Plots plots, List<String> themes, long ticks) {
        this.plots = plots;
        this.themes = List.copyOf(themes);
        this.ticks = ticks;
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public void enable(MatchContext context) {
        var seconds = Formatter.number("seconds", ticks / 20);
        if (themes.isEmpty()) {
            context.messages().send(context.audience(), "build-battle.free-build", seconds);
        } else {
            var theme = themes.get(ThreadLocalRandom.current().nextInt(themes.size()));
            context.messages().send(context.audience(), "build-battle.theme", Placeholder.unparsed("theme", theme), seconds);
        }
        var teams = context.roster().teamsLeft();
        for (int i = 0; i < teams.size(); i++) {
            // The mode loader made sure the arena has a plot for every team a match can have.
            var region = context.region(BuildBattle.PLOT + "-" + (i + 1)).orElseThrow();
            var builders = teams.get(i).ids();
            plots.claim(region, teams.get(i).id(), builders);
            var start = plots.of(builders.getFirst()).orElseThrow().start();
            builders.forEach(id -> context.player(id).ifPresent(player -> {
                player.teleport(start);
                player.setGameMode(GameMode.CREATIVE);
            }));
        }
        context.scope().register(() -> plots.all().stream().flatMap(plot -> plot.builders().stream())
                .forEach(id -> context.player(id).ifPresent(player -> {
                    player.getInventory().clear();
                    player.setGameMode(GameMode.ADVENTURE);
                })));

        context.on(BlockPlaceEvent.class, event -> {
            List<BlockState> replaced = event instanceof BlockMultiPlaceEvent multi
                    ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
            if (!replaced.stream().allMatch(state -> plots.contains(event.getPlayer().getUniqueId(), state.getLocation()))) {
                event.setCancelled(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            if (!plots.contains(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        });
        context.on(PlayerMoveEvent.class, event -> {
            var id = event.getPlayer().getUniqueId();
            if (plots.contains(id, event.getTo())) {
                return;
            }
            // Someone who got outside some other way, such as by respawning at a spawn, is brought home.
            if (plots.contains(id, event.getFrom())) {
                event.setCancelled(true);
            } else {
                plots.of(id).ifPresent(plot -> event.setTo(plot.start()));
            }
        });
        context.on(PlayerDropItemEvent.class, event -> event.setCancelled(true));
        context.on(MatchRejoinEvent.class, event -> plots.of(event.getPlayer().getUniqueId())
                .filter(plot -> context.roster().find(event.getPlayer().getUniqueId()).filter(Participant::isAlive).isPresent())
                .ifPresent(plot -> {
                    event.getPlayer().teleport(plot.start());
                    event.getPlayer().setGameMode(GameMode.CREATIVE);
                }));
        // Any item may be used, since the arena's map is pasted back afterwards, but not aimed past the own plot:
        // a bucket, spawn egg or flint and steel acts on the side of the block that was clicked.
        // shortcut: fluids and fire that spread can still cross into a neighbour's plot; cancel their spread once
        // the engine routes block events to matches.
        context.on(PlayerInteractEvent.class, event -> {
            var clicked = event.getClickedBlock();
            if (event.getItem() != null && clicked != null
                    && !plots.contains(event.getPlayer().getUniqueId(), clicked.getRelative(event.getBlockFace()).getLocation())) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        });
    }
}
