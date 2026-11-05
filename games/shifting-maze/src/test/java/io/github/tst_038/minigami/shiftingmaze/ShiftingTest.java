package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShiftingTest {

    // Defaults: shifts every 20 s, 3 moves per round, warned 3 s ahead.
    private static final int SHIFT = 20 * 20;
    private static final int WARNING = 3 * 20;

    private final FakeMatch match = new FakeMatch();
    private final MazeSettings settings = MazeSettings.defaults();
    private final MazeRun run = new MazeRun(settings, 7);
    private final BoundingBox[] box = {new BoundingBox(-100, 0, -100, -99, 2, -99)};
    private final List<Span> opening = new ArrayList<>();
    private final List<Span> closing = new ArrayList<>();
    private final List<Span> stopped = new ArrayList<>();
    private final Telegraph telegraph = new Telegraph(List.of(new Cue() {
        @Override
        public void tick(MatchContext context, Span wall, boolean isOpening, int ticksLeft) {
            var list = isOpening ? opening : closing;
            if (!list.contains(wall)) {
                list.add(wall);
            }
        }

        @Override
        public void stop(MatchContext context, Span wall) {
            stopped.add(wall);
        }
    }));

    @BeforeEach
    void setUp() {
        World world = FakeMatch.proxy(World.class, Map.of());
        match.regions.put("maze", Region.between("maze", world, 0, 64, 0, 60, 69, 60));
        var id = UUID.randomUUID();
        Player runner = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id, "getBoundingBox", (Supplier<BoundingBox>) () -> box[0]));
        match.join(runner);
        match.roster.assignTeam(id, "red");
    }

    private Shifting shifting() {
        return new Shifting(run, settings, new Random(3), telegraph);
    }

    @Test
    void nothingHappensBeforeTheMazeStands() {
        shifting().enable(match);

        match.tick(SHIFT * 3L);

        assertTrue(match.fills.isEmpty());
        assertTrue(opening.isEmpty() && closing.isEmpty());
    }

    @Test
    void oneFillOfTwoBoxesPerShiftComesWhenTheWarningIsOver() {
        run.build(match);
        int built = match.fills.size();
        shifting().enable(match);

        match.tick(SHIFT + WARNING - 1);
        assertEquals(built, match.fills.size());
        assertEquals(3, opening.size());
        assertEquals(3, closing.size());

        match.tick(1);
        assertEquals(built + 2 * settings.shifts(), match.fills.size());
        assertEquals(6, stopped.size(), "the cues are stopped before the walls change");
    }

    @Test
    void theTimerCountsToTheWallsMovingNotToTheWarning() {
        run.build(match);
        shifting().enable(match);
        assertEquals(SHIFT + WARNING, run.nextShiftAt);

        match.tick(SHIFT);
        assertEquals(SHIFT + WARNING, run.nextShiftAt, "the warning has started, the walls move in WARNING ticks");

        match.tick(WARNING);
        assertEquals(SHIFT + WARNING + SHIFT, run.nextShiftAt, "the walls moved, the next ones move a period later");
    }

    @Test
    void aPlayerInTheClosingDoorwayDropsThatMove() {
        run.build(match);
        int built = match.fills.size();
        shifting().enable(match);
        match.tick(SHIFT);
        var doorway = closing.getFirst();
        box[0] = new BoundingBox(doorway.minX() + 0.2, doorway.minY(), doorway.minZ() + 0.2, doorway.minX() + 0.8, doorway.minY() + 1.8, doorway.minZ() + 0.8);

        match.tick(WARNING);

        assertEquals(built + 2 * (settings.shifts() - 1), match.fills.size());
    }

    @Test
    void aRunnerWhoEscapedDoesNotKeepADoorwayOpen() {
        run.build(match);
        int built = match.fills.size();
        shifting().enable(match);
        match.tick(SHIFT);
        var doorway = closing.getFirst();
        box[0] = new BoundingBox(doorway.minX() + 0.2, doorway.minY(), doorway.minZ() + 0.2, doorway.minX() + 0.8, doorway.minY() + 1.8, doorway.minZ() + 0.8);
        run.markEscaped(match.roster.alive().getFirst().id());

        match.tick(WARNING);

        assertEquals(built + 2 * settings.shifts(), match.fills.size());
    }

    @Test
    void everyTelegraphedWallIsStoppedWhenTheScopeCloses() {
        run.build(match);
        shifting().enable(match);
        match.tick(SHIFT + 10);
        Set<Span> telegraphed = new HashSet<>(opening);
        telegraphed.addAll(closing);
        assertEquals(6, telegraphed.size());
        assertTrue(stopped.isEmpty());

        match.scope.close();

        assertEquals(telegraphed, new HashSet<>(stopped));
        assertEquals(6, stopped.size());
        int fills = match.fills.size();
        match.tick(SHIFT);
        assertEquals(fills, match.fills.size(), "the pending shift never happens");
    }

    @Test
    void withoutAWarningTheShiftIsAppliedAtOnce() {
        var quiet = MazeSettings.read(io.github.tst_038.minigami.api.config.ConfigReader.of("", yaml("warning: off")));
        var quietRun = new MazeRun(quiet, 7);
        quietRun.build(match);
        int built = match.fills.size();
        new Shifting(quietRun, quiet, new Random(3), Telegraph.of(quiet)).enable(match);

        match.tick(quiet.shiftSeconds() * 20L);

        assertFalse(match.fills.size() == built);
        assertEquals(built + 2 * quiet.shifts(), match.fills.size());
    }

    private static org.bukkit.configuration.file.YamlConfiguration yaml(String text) {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            config.loadFromString(text);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return config;
    }
}
