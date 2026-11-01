package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.shiftingmaze.blocks.Geometry;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import io.github.tst_038.minigami.shiftingmaze.layout.Cell;
import io.github.tst_038.minigami.shiftingmaze.layout.Generator;
import io.github.tst_038.minigami.shiftingmaze.layout.Landmarks;
import io.github.tst_038.minigami.shiftingmaze.layout.Layout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * The maze of one match, shared by the features that use it: built once from the arena's {@code maze} region
 * and the teams, and what happened to the runners in it. Used from the server thread only.
 */
final class MazeRun {

    private final MazeSettings settings;
    private final long seed;
    private final List<Runnable> whenBuilt = new ArrayList<>();
    private final Set<UUID> escaped = new HashSet<>();
    private final Set<UUID> placed = new HashSet<>();
    private final Map<String, Integer> keysFound = new HashMap<>();
    private final Set<String> announced = new HashSet<>();
    private boolean started;
    private boolean built;
    private Region region;
    private Geometry geometry;
    private Layout layout;
    private Landmarks landmarks;
    private List<String> teams = List.of();

    /** @param seed the same seed in the same arena with the same teams gives the same maze */
    MazeRun(MazeSettings settings, long seed) {
        this.settings = settings;
        this.seed = seed;
    }

    boolean built() {
        return built;
    }

    Geometry geometry() {
        return geometry;
    }

    Layout layout() {
        return layout;
    }

    Landmarks landmarks() {
        return landmarks;
    }

    /** Runs the action once the maze stands, at once if it already does. Never if the maze can't be built. */
    void whenBuilt(Runnable action) {
        if (built) {
            action.run();
        } else {
            whenBuilt.add(action);
        }
    }

    /**
     * Builds the maze with one fill, once; later calls do nothing. If the region can't hold a maze that gives every team its
     * keys, tells everyone and puts everyone out a tick later, so the match ends at once and nothing is built.
     */
    void build(MatchContext context) {
        if (started) {
            return;
        }
        started = true;
        region = context.region(ShiftingMaze.REGION).orElseThrow();
        geometry = new Geometry(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ(),
                settings.cellWidth(), settings.wallThickness(), settings.wallHeight(), settings.blocks().ceiling().isPresent());
        teams = context.roster().teams().stream().flatMap(team -> team.id().stream()).filter(id -> !id.equals(ShiftingMaze.MINOTAUR)).toList();
        if (!geometry.tooSmall()) {
            var random = new Random(seed);
            layout = Generator.generate(geometry.columns(), geometry.rows(), settings.loops(), random);
            landmarks = Landmarks.place(layout, teams.size(), settings.keys(), random);
        }
        if (geometry.tooSmall() || !landmarks.everyTeamHasKeys(settings.keys())) {
            context.messages().send(context.audience(), "shifting-maze.too-small");
            context.scope().register(context.clock().schedule(1, () -> context.players().forEach(context::eliminate)));
            return;
        }
        context.fill(fills()).thenRun(() -> {
            built = true;
            whenBuilt.forEach(Runnable::run);
            whenBuilt.clear();
        });
    }

    /** Floor, the exit's floor, posts, outer walls, closed inner walls and the ceiling, in the order they are placed. */
    private List<Fill> fills() {
        var blocks = settings.blocks();
        var fills = new ArrayList<Fill>();
        fills.add(fill(geometry.floor(), blocks.floor()));
        var exit = geometry.cell(landmarks.exit());
        fills.add(fill(new Span(exit.minX(), exit.minY() - 1, exit.minZ(), exit.maxX(), exit.minY() - 1, exit.maxZ()), blocks.exit()));
        geometry.posts().forEach(span -> fills.add(fill(span, blocks.wall())));
        geometry.boundary().forEach(span -> fills.add(fill(span, blocks.wall())));
        layout.walls().stream().filter(wall -> !layout.isOpen(wall)).forEach(wall -> fills.add(fill(geometry.wall(wall), blocks.wall())));
        geometry.ceiling().ifPresent(span -> fills.add(fill(span, blocks.ceiling().orElseThrow())));
        return fills;
    }

    Fill fill(Span span, Material material) {
        return new Fill(Region.between(region.id(), region.world(), span.minX(), span.minY(), span.minZ(), span.maxX(), span.maxY(), span.maxZ()), material);
    }

    /** The index of a runner team in the order the maze was built for, or -1. */
    int teamIndex(String teamId) {
        return teams.indexOf(teamId);
    }

    /** The runner teams, in the order the maze was built for. */
    List<String> teams() {
        return teams;
    }

    int keysFound(String teamId) {
        return keysFound.getOrDefault(teamId, 0);
    }

    void keyFound(String teamId) {
        keysFound.merge(teamId, 1, Integer::sum);
    }

    /** Whether this is the first time the team's escape is announced. */
    boolean announceEscape(String teamId) {
        return announced.add(teamId);
    }

    /** Sends the message to the team's members who are online. */
    void tell(MatchContext context, String teamId, String key, TagResolver... placeholders) {
        context.roster().teams().stream().filter(team -> team.id().equals(Optional.of(teamId))).flatMap(team -> team.ids().stream())
                .flatMap(id -> context.player(id).stream()).forEach(player -> player.sendMessage(context.messages().render(key, placeholders)));
    }

    boolean escaped(UUID id) {
        return escaped.contains(id);
    }

    void markEscaped(UUID id) {
        escaped.add(id);
    }

    boolean isMinotaur(MatchContext context, UUID id) {
        return teamOf(context, id).equals(Optional.of(ShiftingMaze.MINOTAUR));
    }

    Optional<String> teamOf(MatchContext context, UUID id) {
        return context.roster().find(id).flatMap(Participant::team);
    }

    /** The colour of a team's glow, which is the team's own. */
    Color colour(String teamId) {
        var colour = NamedTextColor.NAMES.value(teamId);
        return colour == null ? Color.WHITE : Color.fromRGB(colour.value());
    }

    /** Where the participant starts: their team's corner, or the lair of the minotaur they are. Empty for a runner without a team. */
    Optional<Location> startOf(MatchContext context, UUID id) {
        var team = teamOf(context, id);
        Optional<Cell> cell = Optional.empty();
        if (team.equals(Optional.of(ShiftingMaze.MINOTAUR))) {
            var minotaurs = context.roster().teams().stream().filter(t -> t.id().equals(team)).findFirst().orElseThrow().ids();
            cell = Optional.of(landmarks.lairs().get(minotaurs.indexOf(id) % landmarks.lairs().size()));
        } else if (team.isPresent() && teamIndex(team.get()) >= 0) {
            cell = Optional.of(landmarks.starts().get(teamIndex(team.get())));
        }
        return cell.map(at -> {
            var spot = geometry.standingSpot(at);
            return new Location(region.world(), spot[0], spot[1], spot[2]);
        });
    }

    /** Whether the participant has been put at their start since the maze stood. */
    boolean placed(UUID id) {
        return placed.contains(id);
    }

    void markPlaced(UUID id) {
        placed.add(id);
    }
}
