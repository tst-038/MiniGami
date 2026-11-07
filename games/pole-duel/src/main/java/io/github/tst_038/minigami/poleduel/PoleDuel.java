package io.github.tst_038.minigami.poleduel;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;

/**
 * Every player stands on a pole of their own, set out at random in the arena. Bridge to the others with slabs and knock
 * them off with a stick; whoever touches the ground is out, and the last one standing wins. Slabs are handed out now and then.
 *
 * <p>The arena needs a region called {@code poles}: its bottom layer is the ground, with room above for the poles and
 * for a player on top. The poles are made new for every match, so the arena needs a saved map to put the ground back.
 *
 * <p>Game settings, all optional: see {@link PoleSettings}.
 */
public final class PoleDuel implements GameType {

    static final String REGION = "poles";
    static final String BUILDING = "building";
    static final String PLAYING = "playing";

    private final PoleSettings settings;

    public PoleDuel() {
        this(PoleSettings.defaults());
    }

    private PoleDuel(PoleSettings settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "pole-duel";
    }

    @Override
    public List<Phase> phases() {
        // The poles go up while the start countdown holds everyone frozen; the cap only guards against a build that never finishes.
        return List.of(Phase.untimed("waiting"), Phase.timed(BUILDING, 60 * 20L), Phase.untimed(PLAYING), Phase.timed("ending", 5 * 20));
    }

    /** Players are put on their poles, frozen there, while the start countdown runs. */
    @Override
    public boolean startsDuringCountdown() {
        return true;
    }

    /** Players start on their poles, not at spawns. */
    @Override
    public boolean usesSpawns() {
        return false;
    }

    @Override
    public GameMode gameMode(String phase) {
        return phase.equals(PLAYING) ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of(BUILDING, List.of(new PoleBuild(settings)), PLAYING, List.of(new Resupply(settings)), "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> regions() {
        return List.of(REGION);
    }

    @Override
    public WinCondition winCondition() {
        return new LastManStanding();
    }

    @Override
    public int lives() {
        return 1;
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new PoleDuel(PoleSettings.read(config));
    }
}
