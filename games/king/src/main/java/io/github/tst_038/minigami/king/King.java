package io.github.tst_038.minigami.king;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Two teams, each with a King chosen at random and guards for the rest. While their King lives a team's members
 * come back after dying (the game's {@code respawn-seconds}); once the King falls, every death of that team is final.
 * The last team with someone alive wins; when time is up the teams are ranked by {@link KingRanking}.
 *
 * <p>The game defines the ranks {@code king} and {@code guard}, so what each is given (armor, weapon, glowing)
 * is up to the game's file. Game setting, optional: {@code fight-seconds} (300).
 */
public final class King implements GameType {

    /** The rank of the one player per team whose death ends the team's respawns. */
    public static final String KING = "king";
    static final String GUARD = "guard";
    /** Times a player killed the other team's King. */
    static final Stat KING_KILLS = new Stat("king-kills");

    private static final Setting<Integer> FIGHT_SECONDS = Setting.of("fight-seconds", Kind.integer(10, 3600), "how long the fight lasts").orElse(300);

    private final int fightSeconds;

    public King() {
        this(ConfigReader.of("", new MemoryConfiguration()));
    }

    private King(ConfigReader config) {
        this.fightSeconds = config.read(FIGHT_SECONDS);
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(FIGHT_SECONDS);
    }

    @Override
    public String id() {
        return "king";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("fighting", fightSeconds * 20L), Phase.timed("ending", 5 * 20));
    }

    @Override
    public GameMode gameMode(String phase) {
        return phase.equals("fighting") ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of("fighting", List.of(new KingDeath(), new KingKills()), "ending", List.of(new TimeUp(), new AnnounceWinner()));
    }

    @Override
    public WinCondition winCondition() {
        return new KingRanking();
    }

    @Override
    public List<String> ranks() {
        return List.of(KING, GUARD);
    }

    @Override
    public OptionalInt requiredTeams() {
        return OptionalInt.of(2);
    }

    @Override
    public Set<Stat> stats() {
        return Set.of(KING_KILLS);
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new King(config);
    }
}
