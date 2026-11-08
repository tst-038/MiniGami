package io.github.tst_038.minigami.features.plotmobs;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.region.TeamRegion;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Lets mobs be part of a build: each may only appear inside a team's region (a plot), at most
 * {@code max} per plot, and stays there. Spawns outside every plot or over the limit are cancelled and
 * the nearest player is told. A mob that gets out anyway, pushed, knocked back or teleporting like an
 * enderman, is put back at the plot's edge within half a second.
 *
 * <p>Mobs are found again by a tag they carry rather than remembered, so the rules hold across phases:
 * enable the feature in every phase mobs should stay. They are removed when the phase that ends the match ends.
 * Minecarts and boats are kept in their plot the same way. Not covered: what a mob does inside its plot, such as arrows, creeper explosions or an enderman taking blocks.
 */
public final class PlotMobs implements Feature {

    static final long CHECK_TICKS = 10;
    /** Far enough to catch an enderman's teleport; a mob that got further is gone for good. */
    private static final double SEARCH_MARGIN = 64;
    private static final double TELL_RANGE = 12;
    private static final String TAG = "minigami-plot:";

    private final int max;

    public PlotMobs(int max) {
        this.max = max;
    }

    enum Verdict { ALLOW, NO_PLOT, FULL }

    static Verdict verdict(Optional<Region> plot, long mobsThere, int max) {
        if (plot.isEmpty()) {
            return Verdict.NO_PLOT;
        }
        return mobsThere >= max ? Verdict.FULL : Verdict.ALLOW;
    }

    static Optional<Region> plotAt(List<Region> plots, Location location) {
        return plots.stream().filter(plot -> plot.contains(location)).findFirst();
    }

    /** The place in the region nearest to the location, standing in the middle of a block like a spawned mob. */
    static Location nearestInside(Region region, Location location) {
        var inside = location.clone();
        inside.setX(Math.clamp(location.getX(), region.minX() + 0.5, region.maxX() + 0.5));
        inside.setY(Math.clamp(location.getY(), region.minY(), region.maxY()));
        inside.setZ(Math.clamp(location.getZ(), region.minZ() + 0.5, region.maxZ() + 0.5));
        return inside;
    }

    @Override
    public void enable(MatchContext context) {
        if (context.teamRegions().isEmpty()) {
            throw new IllegalStateException("plot-mobs needs a game that gives every team a region");
        }
        var plots = context.teamRegions().stream().map(TeamRegion::region).toList();
        context.on(CreatureSpawnEvent.class, event -> {
            if (event.getEntity() instanceof ArmorStand) {
                return;
            }
            var plot = plotAt(plots, event.getLocation());
            var verdict = verdict(plot, plot.map(region -> mobs(region, 0).stream().filter(Mob.class::isInstance).count()).orElse(0L), max);
            if (verdict == Verdict.ALLOW) {
                event.getEntity().addScoreboardTag(tag(plot.get()));
                return;
            }
            event.setCancelled(true);
            if (verdict == Verdict.FULL) {
                nearest(context, event.getLocation()).ifPresent(player -> player.sendMessage(
                        context.messages().render("plot-mobs.limit", Formatter.number("max", max))));
            }
        });
        // Minecarts and boats ride off on their own, so they are kept in their plot like mobs (without counting towards the limit).
        context.on(EntitySpawnEvent.class, event -> {
            if (event.getEntity() instanceof Vehicle) {
                plotAt(plots, event.getLocation()).ifPresentOrElse(plot -> event.getEntity().addScoreboardTag(tag(plot)), () -> event.setCancelled(true));
            }
        });
        context.scope().register(context.clock().repeat(CHECK_TICKS, () -> plots.forEach(plot -> mobs(plot, SEARCH_MARGIN).stream()
                .filter(mob -> !plot.contains(mob.getLocation())).forEach(mob -> putBack(plot, mob)))));
        context.scope().register(() -> {
            if (context.endsMatch()) {
                plots.forEach(plot -> mobs(plot, SEARCH_MARGIN).forEach(Entity::remove));
            }
        });
    }

    private static void putBack(Region plot, Entity mob) {
        // A passenger would otherwise block the teleport, or ride along out of the plot.
        mob.eject();
        mob.teleport(nearestInside(plot, mob.getLocation()));
        mob.setVelocity(new Vector());
    }

    private static String tag(Region plot) {
        return TAG + plot.id();
    }

    /** The plot's mobs within the margin around it. */
    private static Collection<Entity> mobs(Region plot, double margin) {
        var box = new BoundingBox(plot.minX() - margin, plot.minY() - margin, plot.minZ() - margin,
                plot.maxX() + 1 + margin, plot.maxY() + 1 + margin, plot.maxZ() + 1 + margin);
        return plot.world().getNearbyEntities(box, entity -> (entity instanceof Mob || entity instanceof Vehicle) && entity.getScoreboardTags().contains(tag(plot)));
    }

    private static Optional<Player> nearest(MatchContext context, Location where) {
        return context.players().stream().filter(player -> player.getWorld().equals(where.getWorld()))
                .filter(player -> player.getLocation().distanceSquared(where) <= TELL_RANGE * TELL_RANGE)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(where)));
    }
}
