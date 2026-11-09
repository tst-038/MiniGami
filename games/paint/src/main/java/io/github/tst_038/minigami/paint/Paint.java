package io.github.tst_038.minigami.paint;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
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
import org.bukkit.configuration.MemoryConfiguration;

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

    private static final Setting<Integer> PLAY_SECONDS = Setting.of("play-seconds", Kind.integer(10, 3600), "how long the painting lasts").orElse(180);
    private static final Setting<List<Material>> PAINTABLE = Setting.of("paintable", Kind.materials(), "blocks that can be painted over")
            .orElse(List.of(Material.WHITE_CONCRETE));
    private static final Setting<List<Material>> COLORS = Setting.of("colors", Kind.materials(), "a block for each team or player in turn")
            .orElse(DEFAULT_COLORS);

    private final int playSeconds;
    private final Set<Material> paintable;
    private final List<Material> colors;

    public Paint() {
        this(ConfigReader.of("", new MemoryConfiguration()));
    }

    private Paint(ConfigReader config) {
        this.playSeconds = config.read(PLAY_SECONDS);
        this.paintable = Set.copyOf(config.read(PAINTABLE));
        this.colors = config.read(COLORS);
        if (colors.isEmpty()) {
            config.problem(COLORS.key(), "needs at least one block");
        }
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(PLAY_SECONDS, PAINTABLE, COLORS);
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
    public boolean spawnsAnywhere() {
        return true;
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
        return new Paint(config);
    }
}
