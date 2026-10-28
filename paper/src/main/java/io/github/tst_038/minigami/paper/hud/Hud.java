package io.github.tst_038.minigami.paper.hud;

import io.github.tst_038.minigami.api.config.ConfigReader;
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

/**
 * What a game shows its players next to chat: the sidebar lines per phase, a boss bar with the
 * time left, titles, celebration fireworks and sounds. The texts themselves are messages under
 * {@code hud}.
 *
 * @param sidebar line ids per phase id, with {@link #DEFAULT} for the phases not listed
 * @param bossBar empty to show none
 * @param sounds a moment without one is silent
 */
public record Hud(Map<String, List<String>> sidebar, Optional<BossBar.Color> bossBar, boolean titles, boolean fireworks, Map<Moment, Key> sounds) {

    public static final Hud NONE = new Hud(Map.of(), Optional.empty(), false, false, Map.of());
    public static final String DEFAULT = "default";
    /** Lines filled by the engine itself; any stat id is a line too, showing the player's own count. */
    public static final List<String> LINES = List.of("game", "arena", "time", "players", "alive", "team", "lives", "points", "scores", "blank", "footer");

    /** When a sound plays. */
    public enum Moment {
        COUNTDOWN, START, KILL, OUT, WIN, LOSE;

        String configName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Hud {
        sidebar = Map.copyOf(sidebar);
        sounds = sounds.isEmpty() ? Map.of() : new EnumMap<>(sounds);
    }

    /** The lines of the phase; none hides the sidebar. */
    public List<String> sidebar(String phase) {
        return sidebar.getOrDefault(phase, sidebar.getOrDefault(DEFAULT, List.of()));
    }

    /**
     * Reads the {@code hud} key of a game; {@code hud: false} shows nothing.
     *
     * @param phases the game's phases, which the sidebar may set lines for
     * @param stats every stat a game could keep; one the game doesn't keep is simply not shown, so a shared layout can list it
     */
    public static Hud read(ConfigReader game, List<String> phases, Set<String> stats) {
        if (!game.has("hud") || game.isOff("hud")) {
            return NONE;
        }
        var config = game.section("hud");
        var sidebar = new HashMap<String, List<String>>();
        if (config.has("sidebar")) {
            var section = config.section("sidebar");
            var lines = Stream.concat(LINES.stream(), stats.stream()).sorted().toList();
            Stream.concat(phases.stream(), Stream.of(DEFAULT)).forEach(phase -> section.suggest(phase, lines));
            for (String phase : section.keys()) {
                if (!phase.equals(DEFAULT) && !phases.contains(phase)) {
                    section.problem(phase, "unknown phase; the game has: " + String.join(", ", phases) + ", or " + DEFAULT);
                    continue;
                }
                var ids = section.strings(phase);
                ids.stream().filter(id -> !LINES.contains(id) && !stats.contains(id)).forEach(id ->
                        section.problem(phase, "unknown line '" + id + "'; lines: " + String.join(", ", lines)));
                sidebar.put(phase, ids);
            }
        }
        var bossBar = Optional.<BossBar.Color>empty();
        if (config.has("boss-bar") && !config.isOff("boss-bar")) {
            bossBar = Optional.of(config.section("boss-bar").choice("color", BossBar.Color.class));
        }
        var titles = config.has("titles") && config.bool("titles");
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
        return new Hud(sidebar, bossBar, titles, fireworks, sounds);
    }
}
