package io.github.tst_038.minigami.domination;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.DyedBlocks;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.capture.Capture;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.stat.Stat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;

/**
 * Once a second, looks who stands on each capture point (see {@link Capture} for how one is taken);
 * the team that owns a point scores a point for it every second. The whole match sees who owns or is
 * taking what, and hears when a point changes hands. Glass inside a point, such as the pane over a beacon,
 * takes the colour of the team that owns the point and is put back when the point is free or the match is over.
 */
final class CapturePoints implements Feature {

    /** Seconds each player stood on a point their team owned. */
    static final Stat POINT_SECONDS = new Stat("point-seconds");
    /** The points a player scored for their team by holding a capture point alone. */
    static final Stat SCORED = new Stat("capture-points");

    /** The dye that looks like each team colour. */
    private static final Map<TextColor, DyeColor> DYES = Map.ofEntries(
            Map.entry(NamedTextColor.RED, DyeColor.RED), Map.entry(NamedTextColor.DARK_RED, DyeColor.BROWN),
            Map.entry(NamedTextColor.BLUE, DyeColor.BLUE), Map.entry(NamedTextColor.DARK_BLUE, DyeColor.BLUE),
            Map.entry(NamedTextColor.GREEN, DyeColor.LIME), Map.entry(NamedTextColor.DARK_GREEN, DyeColor.GREEN),
            Map.entry(NamedTextColor.YELLOW, DyeColor.YELLOW), Map.entry(NamedTextColor.GOLD, DyeColor.ORANGE),
            Map.entry(NamedTextColor.AQUA, DyeColor.LIGHT_BLUE), Map.entry(NamedTextColor.DARK_AQUA, DyeColor.CYAN),
            Map.entry(NamedTextColor.LIGHT_PURPLE, DyeColor.PINK), Map.entry(NamedTextColor.DARK_PURPLE, DyeColor.PURPLE),
            Map.entry(NamedTextColor.WHITE, DyeColor.WHITE), Map.entry(NamedTextColor.GRAY, DyeColor.LIGHT_GRAY),
            Map.entry(NamedTextColor.DARK_GRAY, DyeColor.GRAY), Map.entry(NamedTextColor.BLACK, DyeColor.BLACK));

    private final Map<String, String> names;
    private final int captureSeconds;
    private final boolean keepCaptured;

    /**
     * @param names the display name, in MiniMessage, of each point by its region
     * @param captureSeconds how long a team must stand on a point alone to take it
     * @param keepCaptured whether a point stays its owner's once it steps off, until another team takes it
     */
    CapturePoints(Map<String, String> names, int captureSeconds, boolean keepCaptured) {
        this.names = names;
        this.captureSeconds = captureSeconds;
        this.keepCaptured = keepCaptured;
    }

    @Override
    public void enable(MatchContext context) {
        var points = names.entrySet().stream().map(entry -> new Point(context.region(entry.getKey()).orElseThrow(), entry.getValue(), new Capture<>(captureSeconds, keepCaptured))).toList();
        context.scope().register(() -> points.forEach(point -> point.glass.restore()));
        context.scope().register(context.clock().repeat(20, () -> {
            var teams = context.roster().teams();
            var bar = new ArrayList<Component>();
            for (Point point : points) {
                var onPoint = context.players().stream().filter(player -> point.region.contains(player.getLocation())).toList();
                var present = onPoint.stream().map(player -> key(teamOf(teams, player))).distinct().toList();
                var alone = present.size() == 1 ? onPoint.getFirst() : null;
                if (point.capture.tick(present)) {
                    point.ownerName = name(context, teamOf(teams, alone), alone);
                    context.messages().send(context.audience(), "domination.taken", Placeholder.component("team", point.ownerName), Placeholder.parsed("point", point.name));
                }
                var wanted = point.capture.owner().flatMap(owner -> dyeOf(context, teams, owner));
                if (!wanted.equals(point.tint)) {
                    point.tint = wanted;
                    wanted.ifPresentOrElse(point.glass::paint, point.glass::restore);
                }
                if (alone != null && point.capture.owner().equals(present.stream().findFirst())) {
                    // Whoever of the owners last stood on it alone scores for the team.
                    point.scorer = alone.getUniqueId();
                    onPoint.forEach(player -> context.count(player.getUniqueId(), POINT_SECONDS));
                }
                if (point.capture.owner().isPresent()) {
                    context.addPoints(point.scorer, 1);
                }
                bar.add(state(context, point, teams, alone, present.size()));
            }
            context.audience().sendActionBar(Component.join(JoinConfiguration.separator(context.messages().render("domination.separator")), bar));
        }));
    }

    /** The colour of the team that owns a point; none for a player on their own, who has no team colour. */
    private static Optional<DyeColor> dyeOf(MatchContext context, List<MatchTeam> teams, String owner) {
        return teams.stream().filter(team -> team.id().equals(Optional.of(owner))).findFirst()
                .flatMap(team -> Optional.ofNullable(DYES.get(context.teamName(owner).color())));
    }

    private static Component state(MatchContext context, Point point, List<MatchTeam> teams, Player alone, int present) {
        var name = Placeholder.parsed("point", point.name);
        if (present > 1) {
            return context.messages().render("domination.contested", name);
        }
        if (point.capture.taker().isPresent() && alone != null) {
            return context.messages().render("domination.capturing", name, Placeholder.component("team", name(context, teamOf(teams, alone), alone)),
                    Formatter.number("seconds", point.capture.progress()), Formatter.number("needed", point.capture.seconds()));
        }
        return point.capture.owner().isPresent() ? context.messages().render("domination.holding", name, Placeholder.component("team", point.ownerName))
                : context.messages().render("domination.free", name);
    }

    /** Every player in the match is in one of its teams, if only a team of one. */
    private static MatchTeam teamOf(List<MatchTeam> teams, Player player) {
        return teams.stream().filter(team -> team.ids().contains(player.getUniqueId())).findFirst().orElseThrow();
    }

    /** Stays the same while members live and die, unlike the team itself. */
    private static String key(MatchTeam team) {
        return team.id().orElseGet(() -> team.ids().getFirst().toString());
    }

    /** The team's name, or for a team of one the player's. */
    private static Component name(MatchContext context, MatchTeam team, Player member) {
        return team.id().map(context::teamName).orElseGet(member::name);
    }

    private static final class Point {
        final Region region;
        final String name;
        final Capture<String> capture;
        Component ownerName = Component.empty();
        UUID scorer;
        /** The glass in the region as it was at the start, to colour and put back. */
        final DyedBlocks glass;
        Optional<DyeColor> tint = Optional.empty();

        Point(Region region, String name, Capture<String> capture) {
            this.region = region;
            this.name = name;
            this.capture = capture;
            this.glass = DyedBlocks.scan(region, DyedBlocks.GLASS);
        }
    }
}
