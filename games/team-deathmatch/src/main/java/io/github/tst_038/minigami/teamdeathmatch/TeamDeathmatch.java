package io.github.tst_038.minigami.teamdeathmatch;

import org.bukkit.GameMode;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.MostPoints;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;

/**
 * A timed fight in which every kill gives the killer's team a point; the team with the most kills
 * when time is up wins. Players always respawn. Without teams in the game, everyone fights for
 * themselves.
 *
 * <p>Game settings, all optional: {@code fight-seconds} (300) and {@code kills-to-win}, which ends the
 * fight as soon as a team has that many kills (none).
 */
public final class TeamDeathmatch implements GameType {

    private final int fightSeconds;
    private final int killsToWin;

    public TeamDeathmatch() {
        this(300, 0);
    }

    private TeamDeathmatch(int fightSeconds, int killsToWin) {
        this.fightSeconds = fightSeconds;
        this.killsToWin = killsToWin;
    }

    @Override
    public String id() {
        return "team-deathmatch";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("fighting", fightSeconds * 20L), Phase.timed("ending", 5 * 20));
    }

    /** Players may fight and, where the variant allows it, dig and build. */
    @Override
    public GameMode gameMode(String phase) {
        return phase.equals("fighting") ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of("fighting", List.of(new Kills()), "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public WinCondition winCondition() {
        return new MostPoints(killsToWin);
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new TeamDeathmatch(
                config.has("fight-seconds") ? config.integer("fight-seconds", 10, 3600) : fightSeconds,
                config.has("kills-to-win") ? config.integer("kills-to-win", 1, 1000) : killsToWin);
    }
}
