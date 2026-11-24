package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.api.config.ConfigLocations;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.koth.hill.Dye;
import io.github.tst_038.minigami.koth.hill.Scoring;
import io.github.tst_038.minigami.koth.schedule.Plan;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;

/** Reads {@code config.yml} into a {@link KothConfig}. */
public final class KothConfigReader {

    /** The most blocks the hill may cover. */
    static final int MAX_POINT_BLOCKS = 4096;
    /** The most blocks the colour box may cover; it is scanned and repainted on every capture. */
    static final int MAX_COLOUR_BLOCKS = 32_768;
    private static final int MAX_SECONDS = 3600;

    private KothConfigReader() {
    }

    /**
     * Looks up the world, so run it on the server thread.
     *
     * @param root a reader of the whole file; checked here, so it must not have been checked yet
     * @throws InvalidConfigException listing every problem in the file, which is then not usable at all
     */
    public static KothConfig read(ConfigReader root, WorldLookup worlds) throws InvalidConfigException {
        var worldSection = root.section("world");
        var world = ConfigLocations.world(worldSection, "name", name -> worlds.load(name).orElse(null));
        var spawn = worldSection.has("spawn")
                ? Optional.of(ConfigLocations.read(worldSection.section("spawn"), world))
                : Optional.<Location>empty();
        boolean respawnHere = !worldSection.has("respawn-here") || worldSection.bool("respawn-here");

        var schedule = root.section("schedule");
        var timetable = TimetableReader.read(schedule);
        var warnings = TimetableReader.warnings(schedule);
        var restrictions = RestrictionReader.read(root.sectionOrEmpty("restrictions"));

        var point = point(root.section("point"), world);
        var scoringSection = root.section("scoring");
        var scoring = new Scoring(scoringSection.integer("points", 1, Integer.MAX_VALUE), scoringSection.integer("every-seconds", 1, MAX_SECONDS));
        var sidebar = root.section("sidebar");
        var lines = new SidebarLines(sidebar.string("title"), sidebar.strings("inactive"), sidebar.strings("active"));

        root.check();
        // check() passed, so nothing above went wrong and the timetable is there.
        return new KothConfig(world, spawn, respawnHere, new Plan(timetable.orElseThrow(), warnings, restrictions), point, scoring, lines);
    }

    private static PointSettings point(ConfigReader section, World world) {
        var region = box(section, "region", world, MAX_POINT_BLOCKS);
        int captureSeconds = section.integer("capture-seconds", 1, MAX_SECONDS);
        var colourBlocks = section.has("colour-blocks")
                ? Optional.of(box(section, "colour-blocks", world, MAX_COLOUR_BLOCKS))
                : Optional.<Region>empty();
        var neutral = section.has("neutral-colour") ? section.choice("neutral-colour", Dye.class) : Dye.WHITE;
        return new PointSettings(region, captureSeconds, colourBlocks, neutral);
    }

    private static Region box(ConfigReader parent, String key, World world, int maxBlocks) {
        var region = Region.read(key, parent.section(key), world);
        // Each side is cut off at the cap, so the product can't overflow however far apart the corners are.
        long blocks = Math.min(region.maxX() - region.minX() + 1L, maxBlocks + 1L)
                * Math.min(region.maxY() - region.minY() + 1L, maxBlocks + 1L)
                * Math.min(region.maxZ() - region.minZ() + 1L, maxBlocks + 1L);
        if (blocks > maxBlocks) {
            parent.problem(key, "covers more than " + maxBlocks + " blocks");
        }
        return region;
    }
}
