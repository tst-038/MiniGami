package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.buildbattle.BuildBattle;
import io.github.tst_038.minigami.domination.Domination;
import io.github.tst_038.minigami.features.anticamping.AntiCampingFactory;
import io.github.tst_038.minigami.features.breakblocks.BreakBlocksFactory;
import io.github.tst_038.minigami.features.countdown.CountdownFactory;
import io.github.tst_038.minigami.features.deathitems.DeathItemsFactory;
import io.github.tst_038.minigami.features.decayfloor.DecayFloorFactory;
import io.github.tst_038.minigami.features.effects.EffectsFactory;
import io.github.tst_038.minigami.features.freeze.FreezeFactory;
import io.github.tst_038.minigami.features.givedrops.GiveDropsFactory;
import io.github.tst_038.minigami.features.kit.KitFactory;
import io.github.tst_038.minigami.features.kit.SavedKit;
import io.github.tst_038.minigami.features.nodamage.NoDamageFactory;
import io.github.tst_038.minigami.features.nohunger.NoHungerFactory;
import io.github.tst_038.minigami.features.nomobs.NoMobsFactory;
import io.github.tst_038.minigami.features.plotmobs.PlotMobsFactory;
import io.github.tst_038.minigami.features.onehit.OneHitFactory;
import io.github.tst_038.minigami.features.projectilebreaks.ProjectileBreaksFactory;
import io.github.tst_038.minigami.features.playarea.PlayAreaFactory;
import io.github.tst_038.minigami.features.teamregion.TeamRegionFactory;
import io.github.tst_038.minigami.features.voidkills.VoidKillsFactory;
import io.github.tst_038.minigami.hideandseek.HideAndSeek;
import io.github.tst_038.minigami.laststanding.LastStanding;
import io.github.tst_038.minigami.stats.combat.CombatStats;
import io.github.tst_038.minigami.teamdeathmatch.TeamDeathmatch;
import io.github.tst_038.minigami.thewall.TheWall;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The game types, features and stat groups that ship with the plugin. */
final class BuiltIns {

    private BuiltIns() {
    }

    static List<GameType> gametypes() {
        return List.of(new LastStanding(), new BuildBattle(), new TeamDeathmatch(), new HideAndSeek(), new Domination(), new TheWall());
    }

    /** @param savedKits the kits saved with {@code /mg kit save}, by name */
    static List<FeatureFactory> features(Function<String, Optional<SavedKit>> savedKits) {
        return List.of(new VoidKillsFactory(), new KitFactory(savedKits), new DeathItemsFactory(), new BreakBlocksFactory(), new NoDamageFactory(),
                new GiveDropsFactory(), new ProjectileBreaksFactory(), new AntiCampingFactory(), new DecayFloorFactory(), new NoHungerFactory(),
                new OneHitFactory(), new CountdownFactory(), new FreezeFactory(), new EffectsFactory(), new NoMobsFactory(), new PlotMobsFactory(),
                new TeamRegionFactory(), new PlayAreaFactory());
    }

    static List<StatGroup> statGroups() {
        return List.of(new CombatStats());
    }
}
