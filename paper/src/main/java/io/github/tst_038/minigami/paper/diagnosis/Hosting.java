package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.loot.LootContainers;
import io.github.tst_038.minigami.api.loot.LootTables;
import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.paper.arena.Arena;
import java.util.List;
import java.util.function.Supplier;

/**
 * An arena listed by a game or variant, as the checks see it: what the game asks of the arena.
 *
 * @param gametype what the game's code needs of the arena: its regions, region groups and a region per team
 * @param mostTeams the most teams a match of it can have; without teams, a player is a team of one
 * @param hasTeams whether it plays in teams set with {@code teams:}, which shouldn't share a spawn
 * @param changesBlocks whether one of its features changes blocks, which only the arena's saved map puts back
 * @param featureRegions the regions its features rely on, such as the one a play area keeps players in
 * @param needsSpawn whether the arena must have a spawn: the game starts players on them, or they wait at one
 * @param usesLoot whether one of its features fills the arena's loot containers
 * @param features its features, to ask for the region groups they rely on and for checks of their own
 */
public record Hosting(Arena arena, GameType gametype, int mostTeams, boolean hasTeams, boolean changesBlocks, List<String> featureRegions,
        boolean needsSpawn, boolean usesLoot, List<Feature> features) {

    public Hosting(Arena arena, GameType gametype, int mostTeams, boolean hasTeams, boolean changesBlocks, List<String> featureRegions,
            boolean needsSpawn, boolean usesLoot) {
        this(arena, gametype, mostTeams, hasTeams, changesBlocks, featureRegions, needsSpawn, usesLoot, List.of());
    }

    public Hosting(Arena arena, GameType gametype, int mostTeams, boolean hasTeams, boolean changesBlocks, List<String> featureRegions,
            boolean needsSpawn) {
        this(arena, gametype, mostTeams, hasTeams, changesBlocks, featureRegions, needsSpawn, false);
    }

    public Hosting(Arena arena, GameType gametype, int mostTeams, boolean hasTeams, boolean changesBlocks, List<String> featureRegions) {
        this(arena, gametype, mostTeams, hasTeams, changesBlocks, featureRegions, true);
    }

    public Hosting(Arena arena, GameType gametype, int mostTeams, boolean hasTeams, boolean changesBlocks) {
        this(arena, gametype, mostTeams, hasTeams, changesBlocks, List.of());
    }

    /**
     * Every check an arena goes through for a game, in the order their findings are listed.
     *
     * @param worldEdit whether WorldEdit or FastAsyncWorldEdit is installed
     * @param lootTables the loot tables as currently loaded
     */
    public static List<Check<Hosting>> checks(boolean worldEdit, Supplier<LootTables> lootTables) {
        return List.of(new SpawnsCheck(), new RegionsCheck(), new RegionGroupsCheck(), new TeamRegionsCheck(), new MapCheck(), new MapCoversCheck(),
                new WorldEditCheck(worldEdit), new TeamSpawnsCheck(), new FeatureCheck(),
                new LootCheck(lootTables, point -> LootContainers.isContainer(point.world().getBlockAt(point.x(), point.y(), point.z()).getType())));
    }

    /** The {@code /mg arena <id> …} command that ends in {@code action}. */
    String command(String action) {
        return "/mg arena " + arena.id() + " " + action;
    }
}
