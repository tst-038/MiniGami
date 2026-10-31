package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.api.region.TeamRegion;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Every team builds on its plot, the region it was handed, together in creative, on a theme picked at
 * random. A player without a team builds alone. Keeping to the plot is up to the {@code team-region}
 * feature.
 */
final class Building implements Feature {

    private final List<String> themes;
    private final long ticks;

    Building(List<String> themes, long ticks) {
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
        for (TeamRegion plot : context.teamRegions()) {
            var start = plot.region().standingSpot();
            plot.members().forEach(id -> context.player(id).ifPresent(player -> {
                player.teleport(start);
                player.setGameMode(GameMode.CREATIVE);
            }));
        }
        context.scope().register(() -> context.teamRegions().stream().flatMap(plot -> plot.members().stream())
                .forEach(id -> context.player(id).ifPresent(player -> {
                    player.getInventory().clear();
                    player.setGameMode(GameMode.ADVENTURE);
                })));

        context.on(PlayerDropItemEvent.class, event -> event.setCancelled(true));
        context.on(MatchRejoinEvent.class, event -> context.teamRegion(event.getPlayer().getUniqueId())
                .filter(plot -> context.roster().find(event.getPlayer().getUniqueId()).filter(Participant::isAlive).isPresent())
                .ifPresent(plot -> {
                    event.getPlayer().teleport(plot.region().standingSpot());
                    event.getPlayer().setGameMode(GameMode.CREATIVE);
                }));
    }
}
