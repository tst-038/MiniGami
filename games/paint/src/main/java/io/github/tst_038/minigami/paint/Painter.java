package io.github.tst_038.minigami.paint;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.footprint.Footprint;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.territory.Territory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Walking paints: the canvas blocks a player stands on, if paintable or already coloured, turn the
 * player's colour. Each block is a point for the painter and one less for whoever held it. Only
 * blocks of the {@code canvas} regions count.
 */
final class Painter implements Feature {

    /** The regions that can be painted; an arena has any number. */
    static final String CANVAS = "canvas";

    /** Blocks a player coloured, including those taken from others. */
    static final Stat PAINTED = new Stat("blocks-painted");
    /** Blocks a player painted over that another team held. */
    static final Stat STOLEN = new Stat("blocks-stolen");

    private final Set<Material> canPaint;
    private final List<Material> colors;

    Painter(Set<Material> paintable, List<Material> colors) {
        this.colors = List.copyOf(colors);
        var canPaint = new HashSet<>(paintable);
        canPaint.addAll(colors);
        this.canPaint = Set.copyOf(canPaint);
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public List<String> regionGroups() {
        return List.of(CANVAS);
    }

    @Override
    public void enable(MatchContext context) {
        var territory = new Territory();
        var canvas = context.regions(CANVAS);
        var leader = new String[1];
        context.scope().register(context.clock().repeat(1, () -> {
            boolean changed = false;
            for (var player : context.players()) {
                if (context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent()) {
                    changed |= paintUnder(context, territory, canvas, player);
                }
            }
            if (changed) {
                announceLeader(context, leader);
            }
        }));
    }

    /** @return whether any block changed hands */
    private boolean paintUnder(MatchContext context, Territory territory, List<Region> canvas, Player player) {
        var id = player.getUniqueId();
        var color = colorOf(context, id);
        var lost = new HashMap<UUID, Integer>();
        int painted = 0;
        int stolen = 0;
        for (var block : Footprint.under(player.getLocation())) {
            if (!canPaint.contains(block.getType()) || !onCanvas(canvas, block)) {
                continue;
            }
            var previous = territory.paint(Territory.pack(block.getX(), block.getY(), block.getZ()), id);
            if (previous.filter(id::equals).isPresent()) {
                continue;
            }
            block.setType(color, false);
            painted++;
            if (previous.isPresent()) {
                lost.merge(previous.get(), 1, Integer::sum);
                if (colorOf(context, previous.get()) != color) {
                    stolen++;
                }
            }
        }
        if (painted == 0) {
            return false;
        }
        context.addPoints(id, painted);
        lost.forEach((owner, blocks) -> context.addPoints(owner, -blocks));
        context.count(id, PAINTED, painted);
        if (stolen > 0) {
            context.count(id, STOLEN, stolen);
        }
        return true;
    }

    private static boolean onCanvas(List<Region> canvas, Block block) {
        return canvas.stream().anyMatch(region -> block.getWorld().equals(region.world())
                && block.getX() >= region.minX() && block.getX() <= region.maxX()
                && block.getY() >= region.minY() && block.getY() <= region.maxY()
                && block.getZ() >= region.minZ() && block.getZ() <= region.maxZ());
    }

    /** Every team, and every player without one, takes the next colour in turn. */
    private Material colorOf(MatchContext context, UUID id) {
        var teams = context.roster().teams();
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).ids().contains(id)) {
                return colors.get(i % colors.size());
            }
        }
        return colors.getFirst();
    }

    /** Tells everyone when a team or player takes the lead alone. */
    private static void announceLeader(MatchContext context, String[] leader) {
        var scores = context.scores();
        MatchTeam best = null;
        int bestPoints = 0;
        boolean tied = false;
        for (MatchTeam team : context.roster().teams()) {
            int points = team.ids().stream().mapToInt(scores::of).sum();
            if (best == null || points > bestPoints) {
                best = team;
                bestPoints = points;
                tied = false;
            } else if (points == bestPoints) {
                tied = true;
            }
        }
        if (best == null || tied || bestPoints <= 0) {
            return;
        }
        var winner = best;
        var key = winner.id().orElseGet(() -> winner.ids().getFirst().toString());
        if (!key.equals(leader[0])) {
            leader[0] = key;
            context.messages().send(context.audience(), "paint.leader", Placeholder.component("team", name(context, winner)), Formatter.number("blocks", bestPoints));
        }
    }

    /** The team's name, or for a player without one their own. */
    private static Component name(MatchContext context, MatchTeam team) {
        return team.id().map(context::teamName)
                .orElseGet(() -> context.player(team.ids().getFirst()).map(context::playerName).orElse(Component.text("?")));
    }
}
