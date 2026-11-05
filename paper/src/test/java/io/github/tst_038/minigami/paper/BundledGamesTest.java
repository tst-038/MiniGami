package io.github.tst_038.minigami.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.loot.LootTables;
import io.github.tst_038.minigami.api.terrain.TerrainStyles;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.diagnosis.Hosting;
import io.github.tst_038.minigami.paper.game.GameLoader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.Test;

class BundledGamesTest {

    private YamlConfiguration resource(String path) throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }

    @Test
    void everyExampleGameLoadsOnTheFirstStart() throws Exception {
        var gametypes = new IdRegistry<GameType>(GameType::id);
        BuiltIns.gametypes().forEach(gametypes::register);
        var features = new IdRegistry<FeatureFactory>(FeatureFactory::id);
        for (var feature : BuiltIns.features(name -> Optional.empty(), () -> LootTables.NONE, name -> Optional.empty(),
                () -> TerrainStyles.NONE, Runnable::run, message -> { }, Audience::empty)) {
            // Features check block, item and effect names against the server's registry, which tests don't have,
            // so only their names are checked here; their settings are checked when the server loads them.
            features.register(new FeatureFactory() {
                @Override
                public String id() {
                    return feature.id();
                }

                @Override
                public Feature create(ConfigReader config) {
                    config.keys();
                    return context -> { };
                }
            });
        }
        var statGroups = new IdRegistry<StatGroup>(StatGroup::id);
        BuiltIns.statGroups().forEach(statGroups::register);
        var arenas = new IdRegistry<Arena>(Arena::id);
        arenas.register(new Arena("example", null, List.of(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty()));
        var files = new HashMap<String, YamlConfiguration>();
        for (var file : MiniGamiPlugin.EXAMPLE_GAMES) {
            files.put(file.replace(".yml", ""), resource("games/" + file));
        }
        var messages = resource("messages/en.yml");

        var loaded = new GameLoader(gametypes, features, statGroups, Hosting.checks(true, () -> io.github.tst_038.minigami.api.loot.LootTables.NONE))
                .load(new YamlFiles("games", files, List.of()), arenas, false, resource("config.yml").getConfigurationSection("hud"), messages::isString);

        assertEquals(List.of(), loaded.problems());
        assertEquals(MiniGamiPlugin.EXAMPLE_GAMES.size(), loaded.entries().ids().size());
    }
}
