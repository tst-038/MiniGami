package io.github.tst_038.minigami.paper.hud;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;

/**
 * What a game shows its players next to chat: the sidebar per phase, a boss bar with the time left,
 * titles, celebration fireworks and sounds. Sidebar and boss bar texts are written right here, in
 * MiniMessage with the match's values such as {@code %time%}; titles are messages under {@code hud.title}.
 *
 * @param sidebarTitle the sidebar's heading
 * @param sidebar lines per phase id, with {@link #STARTING} for the countdown before the start and
 *     {@link #DEFAULT} for the phases not listed
 * @param bossBar empty to show none
 * @param titles the timing of the event titles, empty to show none; the countdown and paused titles keep their own
 * @param sounds a moment without one is silent
 * @param stats every stat id a text may name; a stat the match doesn't keep counts as empty, so a shared layout can list it
 */
public record Hud(String sidebarTitle, Map<String, List<String>> sidebar, Optional<Bar> bossBar, Optional<Title.Times> titles, boolean fireworks,
        Map<Moment, Key> sounds, Set<String> stats) {

    public static final Hud NONE = new Hud("", Map.of(), Optional.empty(), Optional.empty(), false, Map.of(), Set.of());
    public static final String DEFAULT = "default";
    /** The countdown before the start, which is still the first phase. */
    public static final String STARTING = "starting";
    /** On a line of its own: a line per team by points, or without teams the top three players. */
    public static final String SCORES = "%scores%";
    /** Line names from before lines were texts, to point anyone still using them to the new way. */
    private static final Set<String> OLD_LINES = Set.of("game", "arena", "time", "players", "alive", "team", "lives", "points", "scores", "blank", "footer");

    /** When a sound plays. */
    public enum Moment {
        COUNTDOWN, START, KILL, OUT, WIN, PODIUM, LOSE;

        String configName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A boss bar's colour and its text per phase id, like the sidebar's; a phase without a text shows no bar. */
    public record Bar(BossBar.Color color, Map<String, String> texts) {

        public Bar {
            texts = Map.copyOf(texts);
        }

        public Optional<String> text(String phase) {
            return Optional.ofNullable(texts.getOrDefault(phase, texts.get(DEFAULT)));
        }
    }

    public Hud {
        sidebar = Map.copyOf(sidebar);
        sounds = sounds.isEmpty() ? Map.of() : new EnumMap<>(sounds);
        stats = Set.copyOf(stats);
    }

    /** The lines of the phase; none hides the sidebar. */
    public List<String> sidebar(String phase) {
        return sidebar.getOrDefault(phase, sidebar.getOrDefault(DEFAULT, List.of()));
    }

    /**
     * Reads the {@code hud} key of a game; {@code hud: false} shows nothing.
     *
     * @param phases the game's phases, which the sidebar and boss bar may set texts for
     * @param stats every stat a game could keep
     */
    public static Hud read(ConfigReader game, List<String> phases, Set<String> stats) {
        if (!game.has("hud") || game.isOff("hud")) {
            return NONE;
        }
        var config = game.section("hud");
        var keys = Stream.concat(phases.stream(), Stream.of(STARTING, DEFAULT)).toList();
        var sidebarTitle = "";
        var sidebar = new HashMap<String, List<String>>();
        if (config.has("sidebar")) {
            var section = config.section("sidebar");
            if (section.has("title")) {
                sidebarTitle = section.string("title");
            }
            for (String phase : section.keys()) {
                if (phase.equals("title")) {
                    continue;
                }
                if (!keys.contains(phase)) {
                    section.problem(phase, unknownPhase(phases));
                    continue;
                }
                var lines = section.strings(phase);
                for (String line : lines) {
                    if (OLD_LINES.contains(line) || stats.contains(line)) {
                        section.problem(phase, "'" + line + "': lines are texts now, such as \"<gray>Alive: <white>%alive%\"");
                    } else if (line.contains(SCORES) && !line.equals(SCORES)) {
                        section.problem(phase, SCORES + " must be a line of its own");
                    }
                }
                sidebar.put(phase, lines);
            }
        }
        var bossBar = Optional.<Bar>empty();
        if (config.has("boss-bar") && !config.isOff("boss-bar")) {
            var section = config.section("boss-bar");
            var texts = new HashMap<String, String>();
            if (section.has("text")) {
                var textSection = section.section("text");
                for (String phase : textSection.keys()) {
                    if (keys.contains(phase)) {
                        texts.put(phase, textSection.string(phase));
                    } else {
                        textSection.problem(phase, unknownPhase(phases));
                    }
                }
            }
            bossBar = Optional.of(new Bar(section.choice("color", BossBar.Color.class), texts));
        }
        var titles = Optional.<Title.Times>empty();
        if (config.has("titles") && config.isSection("titles")) {
            var section = config.section("titles");
            var times = Title.DEFAULT_TIMES;
            titles = Optional.of(Title.Times.times(
                    ticks(section, "fade-in", times.fadeIn()), ticks(section, "stay", times.stay()), ticks(section, "fade-out", times.fadeOut())));
        } else if (config.has("titles") && config.bool("titles")) {
            titles = Optional.of(Title.DEFAULT_TIMES);
        }
        var fireworks = config.has("fireworks") && config.bool("fireworks");
        var sounds = new EnumMap<Moment, Key>(Moment.class);
        if (config.has("sounds")) {
            var section = config.section("sounds");
            for (var moment : Moment.values()) {
                var key = moment.configName();
                if (!section.has(key) || section.isOff(key)) {
                    continue;
                }
                var sound = section.string(key);
                // shortcut: only the form is checked, an unknown sound is silent; check the sound registry once loading runs on a server.
                if (Key.parseable(sound)) {
                    sounds.put(moment, Key.key(sound));
                } else if (!sound.isEmpty()) {
                    section.problem(key, "not a sound name, such as entity.player.levelup");
                }
            }
            section.keys().stream().filter(key -> Arrays.stream(Moment.values()).noneMatch(moment -> moment.configName().equals(key)))
                    .forEach(key -> section.problem(key, "unknown moment; moments: "
                            + String.join(", ", Arrays.stream(Moment.values()).map(Moment::configName).toList())));
        }
        return new Hud(sidebarTitle, sidebar, bossBar, titles, fireworks, sounds, stats);
    }

    private static Duration ticks(ConfigReader section, String key, Duration fallback) {
        return section.has(key) ? Duration.ofMillis(section.integer(key, 0, Integer.MAX_VALUE) * 50L) : fallback;
    }

    private static String unknownPhase(List<String> phases) {
        return "unknown phase; the game has: " + String.join(", ", phases) + ", or " + STARTING + " or " + DEFAULT;
    }
}
