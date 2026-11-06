package io.github.tst_038.minigami.thewall;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/**
 * Teams start walled off in their own part of the arena and gather what they can while they prepare;
 * dying then costs nothing. Then the walls sink into the ground, and the last team standing wins. A
 * border can close in during the fight, so it doesn't drag on.
 *
 * <p>Every team has a quarter of the arena, the regions {@code quarter-1}, {@code quarter-2} and so on in
 * team order; the {@code play-area} feature, set for the preparing phase in the game's file, keeps a
 * team in its own until the walls drop.
 *
 * <p>The arena decides which walls there are: every region named {@code wall} or {@code wall-…}, such
 * as {@code wall-north} or a {@code wall-tower} in the middle, drops.
 *
 * <p>Game settings, all optional: {@code prepare-seconds} (900) and {@code border: { end-size: 20, shrink-seconds: 300, damage: 1 }}, which
 * closes the arena's {@code border} region in on its centre, from its widest side down to {@code
 * end-size} blocks, over {@code shrink-seconds}, hurting players outside it each second (no border).
 */
public final class TheWall implements GameType {

    static final String BUILDING = "building";
    static final String PREPARING = "preparing";
    static final String FIGHTING = "fighting";
    static final String WALLS = "wall";
    static final String QUARTER = "quarter";

    private final int prepareSeconds;
    private final Optional<ClosingBorder.Settings> border;

    public TheWall() {
        this(900, Optional.empty());
    }

    private TheWall(int prepareSeconds, Optional<ClosingBorder.Settings> border) {
        this.prepareSeconds = prepareSeconds;
        this.border = border;
    }

    @Override
    public String id() {
        return "the-wall";
    }

    @Override
    public List<Phase> phases() {
        // The land is made while the start countdown holds everyone frozen; the cap only guards against a build that never finishes.
        return List.of(Phase.untimed("waiting"), Phase.timed(BUILDING, 120 * 20L), Phase.timed(PREPARING, prepareSeconds * 20L), Phase.untimed(FIGHTING), Phase.timed("ending", 5 * 20));
    }

    /** The land is made, with the players frozen at their quarters, while the start countdown runs. */
    @Override
    public boolean startsDuringCountdown() {
        return true;
    }

    /** Players dig and build from the preparation on. */
    @Override
    public GameMode gameMode(String phase) {
        return phase.equals(BUILDING) || phase.equals(PREPARING) || phase.equals(FIGHTING) ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    @Override
    public Map<String, List<Feature>> features() {
        Feature prepare = context -> context.messages().send(context.audience(), "the-wall.preparing", Formatter.number("minutes", prepareSeconds / 60.0));
        var fight = new ArrayList<Feature>();
        fight.add(new WallDrop());
        border.ifPresent(settings -> fight.add(new ClosingBorder(settings)));
        return Map.of(BUILDING, List.of(new Built()), PREPARING, List.of(prepare), FIGHTING, fight, "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> regions() {
        return border.stream().map(settings -> ClosingBorder.REGION).toList();
    }

    @Override
    public Optional<String> regionPerTeam() {
        return Optional.of(QUARTER);
    }

    @Override
    public List<String> regionGroups() {
        return List.of(WALLS);
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
    public Optional<String> livesFrom() {
        return Optional.of(FIGHTING);
    }

    @Override
    public GameType configure(ConfigReader config) {
        var closing = config.has("border") ? Optional.of(ClosingBorder.Settings.read(config.section("border"))) : border;
        return new TheWall(                config.has("prepare-seconds") ? config.integer("prepare-seconds", 10, 7200) : prepareSeconds,
                closing);
    }
}
