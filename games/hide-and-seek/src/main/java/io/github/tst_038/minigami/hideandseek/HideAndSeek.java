package io.github.tst_038.minigami.hideandseek;

import org.bukkit.GameMode;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.Survival;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Hiders get a head start to hide, then the seekers hunt them. A hider who dies is caught and out.
 * Seekers win once every hider is caught; hiders still in when time runs out win.
 *
 * <p>The game defines the roles {@code seeker} and {@code hider} and what each gets per phase, such
 * as {@code freeze} and {@code effects} for seekers while the hiders hide, and a kit to catch with.
 * A hider has one life unless the game says otherwise; give seekers {@code lives: 0} so they always
 * come back.
 *
 * <p>Game settings, all optional: {@code hide-seconds} (30) and {@code seek-seconds} (300).
 */
public final class HideAndSeek implements GameType {

    private static final Setting<Integer> HIDE_SECONDS = Setting.of("hide-seconds", Kind.integer(5, 600), "head start the hiders get to hide").orElse(30);
    private static final Setting<Integer> SEEK_SECONDS = Setting.of("seek-seconds", Kind.integer(10, 3600), "how long the seekers hunt").orElse(300);

    private final int hideSeconds;
    private final int seekSeconds;

    public HideAndSeek() {
        this(ConfigReader.of("", new MemoryConfiguration()));
    }

    private HideAndSeek(ConfigReader config) {
        this.hideSeconds = config.read(HIDE_SECONDS);
        this.seekSeconds = config.read(SEEK_SECONDS);
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(HIDE_SECONDS, SEEK_SECONDS);
    }

    @Override
    public String id() {
        return "hide-and-seek";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("hiding", hideSeconds * 20L), Phase.timed("seeking", seekSeconds * 20L),
                Phase.timed("ending", 10 * 20));
    }

    /** Players may fight and, where the variant allows it, dig and build. */
    @Override
    public GameMode gameMode(String phase) {
        return phase.equals("hiding") || phase.equals("seeking") ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of(
                "hiding", List.of(new Announce("hide-and-seek.hiding")),
                "seeking", List.of(new Announce("hide-and-seek.seeking")),
                "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> roles() {
        return List.of("seeker", "hider");
    }

    @Override
    public WinCondition winCondition() {
        return new Survival("hider");
    }

    @Override
    public int lives() {
        return 1;
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new HideAndSeek(config);
    }
}
