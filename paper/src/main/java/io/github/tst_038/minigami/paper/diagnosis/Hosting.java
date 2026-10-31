package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.List;

/**
 * An arena listed by a game or variant, as the checks see it: what the game asks of the arena.
 *
 * @param gametype what the game's code needs of the arena: its regions, region groups and a region per team
 * @param mostTeams the most teams a match of it can have; without teams, a player is a team of one
 * @param hasTeams whether it plays in teams set with {@code teams:}, which shouldn't share a spawn
 * @param changesBlocks whether one of its features changes blocks, which only the arena's saved map puts back
 */
public record Hosting(Arena arena, GameType gametype, int mostTeams, boolean hasTeams, boolean changesBlocks) {

    /**
     * Every check an arena goes through for a game, in the order their findings are listed.
     *
     * @param worldEdit whether WorldEdit or FastAsyncWorldEdit is installed
     */
    public static List<Check<Hosting>> checks(boolean worldEdit) {
        return List.of(new SpawnsCheck(), new RegionsCheck(), new RegionGroupsCheck(), new TeamRegionsCheck(), new MapCheck(),
                new WorldEditCheck(worldEdit), new TeamSpawnsCheck());
    }

    /** The {@code /mg arena <id> …} command that ends in {@code action}. */
    String command(String action) {
        return "/mg arena " + arena.id() + " " + action;
    }
}
