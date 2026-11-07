package io.github.tst_038.minigami.poleduel;

import io.github.tst_038.minigami.api.feature.ArenaLayout;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.bukkit.Location;

/**
 * Sets up a pole for every player at a random place on solid ground in the {@code poles} region, puts them on top and
 * ends the building phase. With no room for all the poles it says so and puts everyone out, so the match ends at once.
 */
final class PoleBuild implements Feature {

    private final PoleSettings settings;

    PoleBuild(PoleSettings settings) {
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        // Waits a tick, so the players are all in place and the other features of the phase have asked for their edits first.
        context.scope().register(context.clock().schedule(1, () -> build(context)));
    }

    private void build(MatchContext context) {
        var players = context.players();
        var region = context.region(PoleDuel.REGION).orElseThrow();
        var poles = tallEnough(region) ? Poles.place(bounds(region), players.size(), settings.minDistance(), new Random(context.random().nextLong()),
                column -> region.world().getBlockAt(column.x(), region.minY(), column.z()).isSolid()) : List.<Poles.Column>of();
        if (poles.isEmpty()) {
            context.messages().send(context.audience(), "pole-duel.no-room");
            context.scope().register(context.clock().schedule(1, () -> players.forEach(context::eliminate)));
            return;
        }
        var fills = poles.stream().map(pole -> new Fill(Region.between("pole", region.world(), pole.x(), region.minY() + 1, pole.z(),
                pole.x(), region.minY() + settings.height(), pole.z()), settings.pole())).toList();
        context.fill(fills).thenRun(() -> {
            if (context.scope().isClosed()) {
                return;
            }
            IntStream.range(0, players.size()).forEach(i -> players.get(i).teleport(
                    new Location(region.world(), poles.get(i).x() + 0.5, region.minY() + settings.height() + 1, poles.get(i).z() + 0.5)));
            context.messages().send(context.audience(), "pole-duel.built");
            context.endPhase();
        });
    }

    /** A pole and a player on top of it fit between the ground and the region's roof. */
    private boolean tallEnough(Region region) {
        return region.maxY() - region.minY() >= settings.height() + 2;
    }

    private static Poles.Bounds bounds(Region region) {
        return new Poles.Bounds(region.minX(), region.minZ(), region.maxX(), region.maxZ());
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    /** Fails when poles for the most players cannot be set out, ignoring whether the ground is solid, which only a running world shows. */
    @Override
    public void diagnose(ArenaLayout arena, Diagnosis diagnosis) {
        for (var region : arena.regions(PoleDuel.REGION)) {
            if (!tallEnough(region) || Poles.place(bounds(region), settings.maxPlayers(), settings.minDistance(), new Random(0)).isEmpty()) {
                diagnosis.blocking("pole-duel-too-small", Map.of("region", region.id(), "players", String.valueOf(settings.maxPlayers()),
                        "height", String.valueOf(settings.height() + 3)));
            }
        }
    }
}
