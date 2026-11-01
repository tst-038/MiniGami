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
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
    private Random random;
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
     * Lays out the maze and starts placing its floor and walls, once; later calls do nothing. Needs no teams, so it can start
     * as soon as the match is about to begin.
     */
    void prepare(MatchContext context) {
        if (region != null) {
            return;
        }
        region = context.region(ShiftingMaze.REGION).orElseThrow();
        geometry = new Geometry(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ(),
                settings.cellWidth(), settings.wallThickness(), settings.wallHeight(), settings.blocks().ceiling().isPresent());
        if (geometry.tooSmall()) {
            return;
        }
        random = new Random(seed);
        layout = Generator.generate(geometry.columns(), geometry.rows(), settings.loops(), random);
        context.fill(walls());
    }

    /**
     * Places the landmarks for the teams and finishes the maze, once; later calls do nothing. If the region can't hold a maze that gives
     * every team its keys, tells everyone and puts everyone out a tick later, so the match ends at once.
     */
    void build(MatchContext context) {
        if (started) {
            return;
        }
        started = true;
        prepare(context);
        teams = context.roster().teams().stream().flatMap(team -> team.id().stream()).filter(id -> !id.equals(ShiftingMaze.MINOTAUR)).toList();
        if (!geometry.tooSmall()) {
            landmarks = Landmarks.place(layout, teams.size(), settings.keys(), random);
        }
        if (geometry.tooSmall() || !landmarks.everyTeamHasKeys(settings.keys())) {
            context.messages().send(context.audience(), "shifting-maze.too-small");
            context.scope().register(context.clock().schedule(1, () -> context.players().forEach(context::eliminate)));
            return;
        }
        var exit = geometry.cell(landmarks.exit());
        // Edits are applied in the order they are asked, so this lands after the walls.
        context.fill(List.of(fill(new Span(exit.minX(), exit.minY() - 1, exit.minZ(), exit.maxX(), exit.minY() - 1, exit.maxZ()), settings.blocks().exit()))).thenRun(() -> {
            built = true;
            whenBuilt.forEach(Runnable::run);
            whenBuilt.clear();
        });
    }

    /** Floor, closed walls with their posts and the ceiling. */
    private List<Fill> walls() {
        var blocks = settings.blocks();
        var fills = new ArrayList<Fill>();
        fills.add(fill(geometry.floor(), blocks.floor()));
        geometry.solids(layout).forEach(span -> fills.add(fill(span, blocks.wall())));
        geometry.ceiling().ifPresent(span -> fills.add(fill(span, blocks.ceiling().orElseThrow())));
        return fills;
    }

    Fill fill(Span span, Map<Material, Integer> mix) {
        return Fill.mixed(box(span), mix);
    }

    private Region box(Span span) {
        return Region.between(region.id(), region.world(), span.minX(), span.minY(), span.minZ(), span.maxX(), span.maxY(), span.maxZ());
    }

    Fill fill(Span span, Material material) {
        return new Fill(box(span), material);
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

    /** Whether the runner's team has found all its keys. */
    boolean hasAllKeys(MatchContext context, UUID id) {
        return teamOf(context, id).filter(team -> keysFound(team) >= settings.keys()).isPresent();
    }

    /** Whether the point is in the exit's safe zone; never when the zone is off. */
    boolean inSafeZone(double x, double z) {
        return settings.safeZoneRadius().isPresent() && geometry.near(landmarks.exit(), settings.safeZoneRadius().get(), x, z);
    }

    /**
     * Announces the team's escape and counts it for those out, if its free members are all out. {@code ignoring} is a member about
     * to be eliminated, so a catch settles the escape before the elimination can end the match.
     */
    void settleEscape(MatchContext context, String team, UUID ignoring) {
        var members = alive(context, team).stream().filter(member -> !member.equals(ignoring)).toList();
        if (!members.isEmpty() && members.stream().allMatch(member -> context.scores().of(member) > 0)) {
            escaped(context, team, members);
        }
    }

    /** Announces the team's escape once and counts it for the members who got out. */
    void escaped(MatchContext context, String team, List<UUID> out) {
        if (announceEscape(team)) {
            context.messages().send(context.audience(), "shifting-maze.escaped", Placeholder.component("team", context.teamName(team)));
            out.forEach(member -> context.count(member, ShiftingMaze.ESCAPES));
        }
    }

    /** The members of the team who have not been caught. */
    List<UUID> alive(MatchContext context, String team) {
        return context.roster().alive().stream().filter(member -> member.team().equals(Optional.of(team))).map(member -> member.id()).toList();
    }

    boolean escaped(UUID id) {
        return escaped.contains(id);
    }

    void markEscaped(UUID id) {
        escaped.add(id);
    }

    /** Tells the minotaurs one message and everyone else another, as the two sides have different goals. */
    void tellRoles(MatchContext context, String runnerKey, String minotaurKey, TagResolver... placeholders) {
        var sides = context.players().stream().collect(java.util.stream.Collectors.partitioningBy(player -> isMinotaur(context, player.getUniqueId())));
        context.messages().send(Audience.audience(sides.get(false)), "shifting-maze." + runnerKey, placeholders);
        context.messages().send(Audience.audience(sides.get(true)), "shifting-maze." + minotaurKey, placeholders);
    }

    boolean isMinotaur(MatchContext context, UUID id) {
        return teamOf(context, id).equals(Optional.of(ShiftingMaze.MINOTAUR));
    }

    Optional<String> teamOf(MatchContext context, UUID id) {
        return context.roster().find(id).flatMap(Participant::team);
    }

    /** The wool that marks what is a team's: the block underneath its keys. */
    Material wool(String teamId) {
        return switch (teamId) {
            case "red" -> Material.RED_WOOL;
            case "blue" -> Material.BLUE_WOOL;
            case "green" -> Material.LIME_WOOL;
            case "yellow" -> Material.YELLOW_WOOL;
            case "aqua" -> Material.LIGHT_BLUE_WOOL;
            case "light_purple" -> Material.PINK_WOOL;
            case "gold" -> Material.ORANGE_WOOL;
            default -> Material.WHITE_WOOL;
        };
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
