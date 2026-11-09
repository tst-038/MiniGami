package io.github.tst_038.minigami.paper.hud;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.kyori.adventure.bossbar.BossBar;

/** The keys of a game's {@code hud} section, as {@link Hud#read} reads them, for completion and checking. */
public final class HudSettings {

    private HudSettings() {
    }

    /** @param phases the game's phases, which the sidebar and boss bar may set texts for */
    public static Setting<ConfigReader> setting(List<String> phases) {
        var keys = Stream.concat(phases.stream(), Stream.of(Hud.STARTING, Hud.DEFAULT)).toList();
        var sidebar = new ArrayList<Setting<?>>(List.of(Setting.of("title", Kind.text(), "the sidebar's heading").orElse("")));
        keys.forEach(phase -> sidebar.add(Setting.of(phase, Kind.strings(), "the sidebar's lines in this phase").orElse(List.of())));
        var barTexts = keys.stream().<Setting<?>>map(phase -> Setting.of(phase, Kind.text(), "the boss bar's text in this phase").orElse("")).toList();
        var times = Stream.of("fade-in", "stay", "fade-out").<Setting<?>>map(key -> Setting.of(key, Kind.integer(0, Integer.MAX_VALUE),
                "ticks of the titles' " + key).orElse(0)).toList();
        var sounds = Arrays.stream(Hud.Moment.values()).<Setting<?>>map(moment -> Setting.of(moment.configName(), Kind.text(),
                "the sound played at this moment; false for none").orElse("")).toList();
        return Setting.of("hud", Kind.section(List.of(
                Setting.of("sidebar", Kind.section(sidebar), "the sidebar's lines per phase: texts with values such as %time%"),
                Setting.of("boss-bar", Kind.section(List.of(
                        Setting.of("color", Kind.choice(BossBar.Color.class), "the boss bar's colour"),
                        Setting.of("text", Kind.section(barTexts), "the boss bar's text per phase"))).offering("false"),
                        "a boss bar with the time left; false for none"),
                Setting.of("titles", Kind.section(times).offering("true", "false"), "titles for the moments of a match, or their timing in ticks"),
                Setting.of("fireworks", Kind.bool(), "fireworks for the winners").orElse(false),
                Setting.of("sounds", Kind.section(sounds), "a sound per moment"))).offering("false"),
                "replaces parts of the HUD from config.yml, or false for none");
    }
}
