package io.github.tst_038.minigami.koth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.koth.hill.Dye;
import io.github.tst_038.minigami.koth.hill.Scoring;
import io.github.tst_038.minigami.koth.restriction.MinOnline;
import io.github.tst_038.minigami.koth.schedule.CycleTimetable;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class KothConfigReaderTest {

    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "koth";
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    private final WorldLookup worlds = name -> name.equals("koth") ? Optional.of(world) : Optional.empty();

    private KothConfig read(String yaml) throws InvalidConfigException {
        return KothConfigReader.read(Yaml.reader(yaml), worlds);
    }

    private List<String> problems(String yaml) {
        var e = assertThrows(InvalidConfigException.class, () -> read(yaml));
        return e.problems().stream().map(ConfigProblem::toString).toList();
    }

    /** The bundled file with one of its lines replaced. */
    private static String bundledWith(String line, String replacement) {
        var yaml = Yaml.bundledConfig();
        assertTrue(yaml.contains(line), line);
        return yaml.replace(line, replacement);
    }

    @Test
    void theBundledConfigReadsWithNoProblems() throws Exception {
        var config = read(Yaml.bundledConfig());

        assertSame(world, config.world());
        assertEquals(5, config.point().captureSeconds());
        assertSame(world, config.point().region().world());
        assertTrue(config.point().colourBlocks().isPresent());
        assertEquals(Dye.WHITE, config.point().neutral());
        assertEquals(new Scoring(1, 1), config.scoring());
        assertEquals(List.of(new MinOnline(3)), config.plan().restrictions());
        assertEquals(List.of(Duration.ofMinutes(60), Duration.ofMinutes(15), Duration.ofMinutes(2)), config.plan().warnings());
        assertEquals(0.5, config.spawnOrDefault().getX());
        assertTrue(config.respawnHere());
        assertEquals(4, config.sidebar().inactive().size());
        assertEquals(2, config.sidebar().active().size());
    }

    @Test
    void theCycleVariantReads() throws Exception {
        var yaml = bundledWith("  type: cron", "  type: cycle\n  active-minutes: 15\n  break-minutes: 5")
                .replace("  when:\n    - \"0 20 * * 1\"    # Mondays 20:00\n    - \"0 20 * * 3\"    # Wednesdays 20:00\n    - \"0 18 * * 6\"    # Saturdays 18:00\n", "")
                .replace("  length-minutes: 60\n", "");

        assertEquals(new CycleTimetable(Duration.ofMinutes(15), Duration.ofMinutes(5)), read(yaml).plan().timetable());
    }

    @Test
    void aMissingWorldNameIsReportedWithoutLookingForAWorld() {
        assertEquals(List.of("config.yml: world.name: is missing"), problems(bundledWith("  name: koth\n", "")));
    }

    @Test
    void aWorldThatCantBeLoadedIsReportedAtItsName() {
        var problems = problems(bundledWith("  name: koth\n", "  name: lobby\n"));

        assertEquals(List.of("config.yml: world.name: no loaded world 'lobby'"), problems);
    }

    @Test
    void aColourBoxOverTheCapIsAProblem() {
        var yaml = bundledWith("colour-blocks: { from: { x: -2, y: 60, z: -2 }, to: { x: 2, y: 63, z: 2 } }",
                "colour-blocks: { from: { x: 0, y: 0, z: 0 }, to: { x: 40, y: 40, z: 40 } }");

        assertEquals(List.of("config.yml: point.colour-blocks: covers more than 32768 blocks"), problems(yaml));
    }

    @Test
    void aHillOverTheCapIsAProblemEvenWhenItSpansTheWorld() {
        var yaml = bundledWith("region: { from: { x: -2, y: 64, z: -2 }, to: { x: 2, y: 66, z: 2 } }",
                "region: { from: { x: -30000000, y: -2048, z: -30000000 }, to: { x: 30000000, y: 2047, z: 30000000 } }");

        assertEquals(List.of("config.yml: point.region: covers more than 4096 blocks"), problems(yaml));
    }

    @Test
    void theColourBoxAndNeutralColourAreOptional() throws Exception {
        var yaml = Yaml.bundledConfig()
                .replaceAll("(?m)^  colour-blocks:.*\\n", "")
                .replaceAll("(?m)^  neutral-colour:.*\\n", "");

        var point = read(yaml).point();

        assertEquals(Optional.empty(), point.colourBlocks());
        assertEquals(Dye.WHITE, point.neutral());
    }

    @Test
    void theNeutralColourIsReadByName() throws Exception {
        assertEquals(Dye.LIGHT_BLUE, read(bundledWith("neutral-colour: white", "neutral-colour: light-blue")).point().neutral());
    }

    @Test
    void zeroMinOnlineAddsNoRestriction() throws Exception {
        assertEquals(List.of(), read(bundledWith("min-online: 3", "min-online: 0")).plan().restrictions());
    }

    @Test
    void theSpawnAndRespawnAreOptional() throws Exception {
        var yaml = Yaml.bundledConfig()
                .replaceAll("(?m)^  spawn:.*\\n", "")
                .replaceAll("(?m)^  respawn-here:.*\\n", "");

        var config = read(yaml);

        assertEquals(Optional.empty(), config.spawn());
        assertTrue(config.respawnHere());
    }

    @Test
    void moreSidebarLinesThanTheClientShowsIsAProblem() {
        var line = "    - \"<white>Time left: <yellow>%time_left%\"";
        var yaml = bundledWith(line, (line + "\n").repeat(15).stripTrailing());

        assertEquals(List.of("config.yml: sidebar.active: has more than 15 lines"), problems(yaml));
    }

    @Test
    void scoringOutOfRangeIsAProblemNotAnException() {
        assertEquals(List.of("config.yml: scoring.every-seconds: must be between 1 and 3600"),
                problems(bundledWith("every-seconds: 1", "every-seconds: 0")));
    }

    @Test
    void everyProblemOfAFileIsListedAtOnce() {
        var yaml = bundledWith("  name: koth\n", "  name: lobby\n")
                .replace("capture-seconds: 5", "capture-seconds: fast")
                .replace("  type: cron", "  type: weekly");

        var problems = problems(yaml);

        assertEquals(3, problems.stream().filter(problem -> problem.startsWith("config.yml: world.name")
                || problem.startsWith("config.yml: point.capture-seconds") || problem.startsWith("config.yml: schedule.type")).count());
    }

    @Test
    void anUnknownKeyIsAProblem() {
        assertEquals(List.of("config.yml: pointt: unknown key"), problems(Yaml.bundledConfig() + "pointt: 1\n"));
    }
}
