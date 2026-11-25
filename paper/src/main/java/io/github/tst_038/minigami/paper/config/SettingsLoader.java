package io.github.tst_038.minigami.paper.config;

import io.github.tst_038.minigami.api.config.ConfigLocations;
import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.ConfigValues;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.paper.config.Settings.LeaderboardSettings;
import io.github.tst_038.minigami.paper.game.Start;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads {@code config.yml}. An invalid file means the defaults are used, so the server still starts. */
public final class SettingsLoader {

    public static final String FILE = "config.yml";
    static final int DEFAULT_BLOCKS_PER_TICK = 8192;
    static final int DEFAULT_PARTY_INVITE_SECONDS = 60;

    private static final Setting<Integer> PARTY_INVITE_SECONDS = Setting.of("invite-seconds", Kind.integer(5, 3600),
            "how long a party invite can be accepted").orElse(DEFAULT_PARTY_INVITE_SECONDS);
    private static final Setting<ConfigReader> PARTIES = Setting.of("parties", Kind.section(List.of(PARTY_INVITE_SECONDS)), "parties");
    private static final Setting<Integer> LEADERBOARD_SIZE = Setting.of("size", Kind.integer(1, 100),
            "how many players a leaderboard shows").orElse(LeaderboardSettings.DEFAULT.size());
    private static final Setting<Integer> LEADERBOARD_REFRESH_SECONDS = Setting.of("refresh-seconds", Kind.integer(10, 86_400),
            "how often leaderboards are read again from storage").orElse(LeaderboardSettings.DEFAULT.refreshSeconds());
    private static final Setting<ConfigReader> LEADERBOARDS = Setting.of("leaderboards", Kind.section(List.of(LEADERBOARD_SIZE, LEADERBOARD_REFRESH_SECONDS)),
            "leaderboards");

    /**
     * The settings, and why the defaults were used instead if the file is invalid.
     *
     * @param keys the keys the file has and the keys reading it asked for
     */
    public record Loaded(Settings settings, List<ConfigProblem> problems, SortedMap<String, List<String>> keys) {
    }

    private SettingsLoader() {
    }

    /** The HUD of the plugin's own {@code config.yml}, under what the admin's file sets, so a file from before a HUD setting existed still gets it. */
    static ConfigurationSection bundledHud() {
        var bundled = new YamlConfiguration();
        try (var in = SettingsLoader.class.getResourceAsStream("/" + FILE)) {
            bundled.loadFromString(new String(Objects.requireNonNull(in, "bundled " + FILE).readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("The bundled " + FILE + " can't be read", e);
        }
        var hud = new YamlConfiguration();
        Optional.ofNullable(bundled.getConfigurationSection("hud")).ifPresent(section -> YamlFiles.overlay(hud, section));
        return hud;
    }

    /** Looks up worlds, so run it on the server thread. */
    public static Loaded load(String yaml, Function<String, World> worlds) {
        var config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            return new Loaded(Settings.DEFAULTS, List.of(new ConfigProblem(FILE, "", "can't be read: " + e.getMessage())),
                    Collections.emptySortedMap());
        }
        var reader = ConfigReader.of(FILE, config);
        reader.suggest("lobby", List.of(ConfigValues.HERE));
        var lobby = reader.has("lobby")
                ? Optional.of(ConfigLocations.readWithWorld(reader.section("lobby"), worlds))
                : Optional.<Location>empty();
        int blocksPerTick = reader.has("blocks-per-tick")
                ? reader.integer("blocks-per-tick", 256, 1_000_000) : DEFAULT_BLOCKS_PER_TICK;
        var start = Start.read(reader, Start.DEFAULT);
        int partyInviteSeconds = reader.read(PARTIES).read(PARTY_INVITE_SECONDS);
        var boards = reader.read(LEADERBOARDS);
        var leaderboards = new LeaderboardSettings(boards.read(LEADERBOARD_SIZE), boards.read(LEADERBOARD_REFRESH_SECONDS));
        var hud = bundledHud();
        if (reader.has("hud")) {
            reader.section("hud").keys();
            var own = config.getConfigurationSection("hud");
            if (own != null) {
                YamlFiles.overlay(hud, own);
            }
        }
        var keys = YamlFiles.keys(config, reader);
        try {
            reader.check();
            return new Loaded(new Settings(lobby, blocksPerTick, hud, start, partyInviteSeconds, leaderboards), List.of(), keys);
        } catch (InvalidConfigException e) {
            return new Loaded(Settings.DEFAULTS, e.problems(), keys);
        }
    }
}
