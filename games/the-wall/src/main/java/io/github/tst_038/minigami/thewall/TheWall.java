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
import java.util.Optional;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/**
 * Teams start walled off in their own part of the arena and gather what they can while they prepare;
 * dying then costs nothing. Then the walls sink into the ground, and the last team standing wins. A
 * border can close in during the fight, so it doesn't drag on.
 *
 * <p>Game settings: {@code walls}, the arena regions that sink (wall); optional are {@code
 * prepare-seconds} (900) and {@code border: { end-size: 20, shrink-seconds: 300, damage: 1 }}, which
 * closes the arena's {@code border} region in on its centre, from its widest side down to {@code
 * end-size} blocks, over {@code shrink-seconds}, hurting players outside it each second (no border).
 */
public final class TheWall implements GameType {

    static final String PREPARING = "preparing";
    static final String FIGHTING = "fighting";

    private final List<String> walls;
    private final int prepareSeconds;
    private final Optional<ClosingBorder.Settings> border;

    public TheWall() {
        this(List.of("wall"), 900, Optional.empty());
    }

    private TheWall(List<String> walls, int prepareSeconds, Optional<ClosingBorder.Settings> border) {
        this.walls = walls;
        this.prepareSeconds = prepareSeconds;
        this.border = border;
    }

    @Override
    public String id() {
        return "the-wall";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed(PREPARING, prepareSeconds * 20L), Phase.untimed(FIGHTING), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Map<String, List<Feature>> features() {
        Feature prepare = context -> context.messages().send(context.audience(), "the-wall.preparing", Formatter.number("minutes", prepareSeconds / 60.0));
        var fight = new ArrayList<Feature>();
        fight.add(new WallDrop(walls));
        border.ifPresent(settings -> fight.add(new ClosingBorder(settings)));
        return Map.of(PREPARING, List.of(prepare), FIGHTING, fight, "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> regions() {
        return Stream.concat(walls.stream(), border.stream().map(settings -> ClosingBorder.REGION)).toList();
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
        var named = config.has("walls") ? config.strings("walls") : walls;
        if (named.isEmpty()) {
            config.problem("walls", "needs at least one region");
        }
        var closing = config.has("border") ? Optional.of(ClosingBorder.Settings.read(config.section("border"))) : border;
        return new TheWall(named,
                config.has("prepare-seconds") ? config.integer("prepare-seconds", 10, 7200) : prepareSeconds,
                closing);
    }
}
