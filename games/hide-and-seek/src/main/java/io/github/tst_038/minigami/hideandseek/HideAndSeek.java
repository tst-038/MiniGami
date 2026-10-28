package io.github.tst_038.minigami.hideandseek;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.Survival;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;

/**
 * Hiders get a head start to hide, then the seekers hunt them. A hider who dies is caught and out.
 * Seekers win once every hider is caught; hiders still in when time runs out win.
 *
 * <p>The mode defines the roles {@code seeker} and {@code hider} and what each gets per phase, such
 * as {@code freeze} and {@code effects} for seekers while the hiders hide, and a kit to catch with.
 * A hider has one life unless the mode says otherwise; give seekers {@code lives: 0} so they always
 * come back.
 *
 * <p>Mode settings, all optional: {@code hide-seconds} (30) and {@code seek-seconds} (300).
 */
public final class HideAndSeek implements GameType {

    private final int hideSeconds;
    private final int seekSeconds;

    public HideAndSeek() {
        this(30, 300);
    }

    private HideAndSeek(int hideSeconds, int seekSeconds) {
        this.hideSeconds = hideSeconds;
        this.seekSeconds = seekSeconds;
    }

    @Override
    public String id() {
        return "hide-and-seek";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("hiding", hideSeconds * 20L), Phase.timed("seeking", seekSeconds * 20L),
                Phase.timed("ending", 5 * 20));
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
    public GameType configure(ConfigReader mode) {
        return new HideAndSeek(
                mode.has("hide-seconds") ? mode.integer("hide-seconds", 5, 600) : hideSeconds,
                mode.has("seek-seconds") ? mode.integer("seek-seconds", 10, 3600) : seekSeconds);
    }
}
