package io.github.tst_038.minigami.domination;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.stat.Stat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Once a second, looks who stands on each capture point. A team alone on a point takes it; the team
 * that owns a point scores a point for it every second. The whole match sees who owns what, and hears
 * when a point changes hands.
 */
final class CapturePoints implements Feature {

    /** Seconds each player stood on a point their team owned. */
    static final Stat POINT_SECONDS = new Stat("point-seconds");

    private final Map<String, String> names;
    private final boolean keepCaptured;

    /**
     * @param names the display name, in MiniMessage, of each point by its region
     * @param keepCaptured whether a point stays its owner's when they step off, until another team takes it
     */
    CapturePoints(Map<String, String> names, boolean keepCaptured) {
        this.names = names;
        this.keepCaptured = keepCaptured;
    }

    @Override
    public void enable(MatchContext context) {
        var points = names.entrySet().stream().map(entry -> new Point(context.region(entry.getKey()).orElseThrow(), entry.getValue())).toList();
        context.scope().register(context.clock().repeat(20, () -> {
            var teams = context.roster().teams();
            var bar = new ArrayList<Component>();
            for (Point point : points) {
                var onPoint = context.players().stream().filter(player -> point.region.contains(player.getLocation())).toList();
                var present = onPoint.stream().map(player -> key(teamOf(teams, player))).distinct().toList();
                var owner = Ownership.next(point.owner, present, keepCaptured);
                if (owner.isPresent() && present.equals(List.of(owner.get()))) {
                    // Whoever stands on it scores for the team, and names it while it has no name of its own.
                    var holder = onPoint.stream().filter(player -> key(teamOf(teams, player)).equals(owner.get())).findFirst().orElseThrow();
                    if (!owner.equals(point.owner)) {
                        point.ownerName = name(context, teamOf(teams, holder), holder);
                        context.messages().send(context.audience(), "domination.taken", Placeholder.component("team", point.ownerName), Placeholder.parsed("point", point.name));
                    }
                    point.scorer = holder.getUniqueId();
                    onPoint.forEach(player -> context.count(player.getUniqueId(), POINT_SECONDS));
                }
                point.owner = owner;
                if (owner.isPresent()) {
                    context.addPoints(point.scorer, 1);
                }
                var state = present.size() > 1 ? context.messages().render("domination.contested")
                        : owner.isPresent() ? point.ownerName : context.messages().render("domination.free");
                bar.add(context.messages().render("domination.point", Placeholder.parsed("point", point.name), Placeholder.component("holder", state)));
            }
            context.audience().sendActionBar(Component.join(JoinConfiguration.separator(context.messages().render("domination.separator")), bar));
        }));
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
        Optional<String> owner = Optional.empty();
        Component ownerName = Component.empty();
        UUID scorer;

        Point(Region region, String name) {
            this.region = region;
            this.name = name;
        }
    }
}
