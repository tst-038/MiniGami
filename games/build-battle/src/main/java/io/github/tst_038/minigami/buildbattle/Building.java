package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
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
 * Every builder claims the plot around the spawn they start on and builds there in creative, on a
 * theme picked at random. Nothing they do reaches past their plot.
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
    public void enable(MatchContext context) {
        var seconds = Formatter.number("seconds", ticks / 20);
        if (themes.isEmpty()) {
            context.messages().send(context.audience(), "build-battle.free-build", seconds);
        } else {
            var theme = themes.get(ThreadLocalRandom.current().nextInt(themes.size()));
            context.messages().send(context.audience(), "build-battle.theme", Placeholder.unparsed("theme", theme), seconds);
        }
        context.roster().alive().stream().map(Participant::id).flatMap(id -> context.player(id).stream()).forEach(player -> {
            plots.claim(player.getUniqueId(), player.getLocation());
            player.setGameMode(GameMode.CREATIVE);
        });
        context.scope().register(() -> plots.builders().forEach(id -> context.player(id).ifPresent(player -> {
            player.getInventory().clear();
            player.setGameMode(GameMode.ADVENTURE);
        })));

        context.on(BlockPlaceEvent.class, event -> {
            List<BlockState> replaced = event instanceof BlockMultiPlaceEvent multi
                    ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
            if (replaced.stream().allMatch(state -> plots.contains(event.getPlayer().getUniqueId(), state.getLocation()))) {
                replaced.forEach(context::rememberOriginal);
            } else {
                event.setCancelled(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            if (plots.contains(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
                context.rememberOriginal(event.getBlock());
            } else {
                event.setCancelled(true);
            }
        });
        context.on(PlayerMoveEvent.class, event -> {
            var id = event.getPlayer().getUniqueId();
            if (plots.contains(id, event.getTo())) {
                return;
            }
            // Someone who got outside some other way, such as by respawning on another spawn, is brought home.
            if (plots.contains(id, event.getFrom())) {
                event.setCancelled(true);
            } else {
                plots.center(id).ifPresent(event::setTo);
            }
        });
        context.on(PlayerDropItemEvent.class, event -> event.setCancelled(true));
        // shortcut: items that aren't blocks (buckets, spawn eggs, flint and steel, armour stands) make fluids,
        // fire and entities the match can't put back; allow them once matches get their own world.
        context.on(PlayerInteractEvent.class, event -> {
            if (event.getItem() != null && !event.getItem().getType().isBlock()) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        });
    }
}
