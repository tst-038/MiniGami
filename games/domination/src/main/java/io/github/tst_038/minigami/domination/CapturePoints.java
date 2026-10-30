package io.github.tst_038.minigami.domination;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.stat.Stat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Once a second, looks who stands on each capture point (see {@link Capture} for how one is taken);
 * the team that owns a point scores a point for it every second. The whole match sees who owns or is
 * taking what, and hears when a point changes hands.
 */
final class CapturePoints implements Feature {

    /** Seconds each player stood on a point their team owned. */
    static final Stat POINT_SECONDS = new Stat("point-seconds");

    private final Map<String, String> names;
    private final int captureSeconds;

    /**
     * @param names the display name, in MiniMessage, of each point by its region
     * @param captureSeconds how long a team must stand on a point alone to take it
     */
    CapturePoints(Map<String, String> names, int captureSeconds) {
        this.names = names;
        this.captureSeconds = captureSeconds;
    }

    @Override
    public void enable(MatchContext context) {
        var points = names.entrySet().stream().map(entry -> new Point(context.region(entry.getKey()).orElseThrow(), entry.getValue(), new Capture<>(captureSeconds))).toList();
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
                if (alone != null && point.capture.owner().equals(present.stream().findFirst())) {
                    // Whoever of the owners last stood on it alone scores for the team.
                    point.scorer = alone.getUniqueId();
                    onPoint.forEach(player -> context.count(player.getUniqueId(), POINT_SECONDS));
                }
                if (point.capture.owner().isPresent()) {
                    context.addPoints(point.scorer, 1);
                }
                bar.add(context.messages().render("domination.point", Placeholder.parsed("point", point.name), Placeholder.component("holder", state(context, point, teams, alone, present.size()))));
            }
            context.audience().sendActionBar(Component.join(JoinConfiguration.separator(context.messages().render("domination.separator")), bar));
        }));
    }

    private static Component state(MatchContext context, Point point, List<MatchTeam> teams, Player alone, int present) {
        if (present > 1) {
            return context.messages().render("domination.contested");
        }
        if (point.capture.taker().isPresent() && alone != null) {
            return context.messages().render("domination.capturing", Placeholder.component("team", name(context, teamOf(teams, alone), alone)),
                    Formatter.number("seconds", point.capture.progress()), Formatter.number("needed", point.capture.seconds()));
        }
        return point.capture.owner().isPresent() ? point.ownerName : context.messages().render("domination.free");
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

        Point(Region region, String name, Capture<String> capture) {
            this.region = region;
            this.name = name;
            this.capture = capture;
        }
    }
}
