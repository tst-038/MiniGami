package io.github.tst_038.minigami.paint;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.win.MostPoints;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Paint the arena's canvas by walking over it: every block you colour is a point, every block of
 * someone else's you paint over takes one from them, so a score is the blocks held. Most points when
 * time is up wins, equal points share a place. Teams paint in one colour, a player without a team in a
 * colour of their own.
 *
 * <p>Game settings, all optional: {@code play-seconds} (180); {@code paintable}, the blocks that can be
 * painted over (white_concrete); {@code colors}, a block for each team or player in turn.
 */
public final class Paint implements GameType {

    private static final List<Material> DEFAULT_COLORS = List.of(Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.LIME_CONCRETE,
            Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.CYAN_CONCRETE, Material.PINK_CONCRETE);

    private final int playSeconds;
    private final Set<Material> paintable;
    private final List<Material> colors;

    public Paint() {
        this(180, Set.of(Material.WHITE_CONCRETE), DEFAULT_COLORS);
    }

    private Paint(int playSeconds, Set<Material> paintable, List<Material> colors) {
        this.playSeconds = playSeconds;
        this.paintable = paintable;
        this.colors = colors;
    }

    @Override
    public String id() {
        return "paint";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("playing", playSeconds * 20L), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Set<Stat> stats() {
        return Set.of(Painter.PAINTED, Painter.STOLEN);
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of("playing", List.of(new Painter(paintable, colors)), "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> regionGroups() {
        return List.of(Painter.CANVAS);
    }

    @Override
    public WinCondition winCondition() {
        return MostPoints.atTimeUp();
    }

    @Override
    public GameType configure(ConfigReader config) {
        var colors = config.has("colors") ? config.materials("colors") : this.colors;
        if (colors.isEmpty()) {
            config.problem("colors", "needs at least one block");
        }
        var paintable = config.has("paintable") ? Set.copyOf(config.materials("paintable")) : this.paintable;
        return new Paint(
                config.has("play-seconds") ? config.integer("play-seconds", 10, 3600) : playSeconds,
                paintable,
                colors);
    }
}
