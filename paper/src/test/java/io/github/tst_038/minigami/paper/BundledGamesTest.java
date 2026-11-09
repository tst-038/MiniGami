package io.github.tst_038.minigami.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.config.setting.Setting;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.configuration.MemoryConfiguration;
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

    private final IdRegistry<GameType> gametypes = new IdRegistry<>(GameType::id);
    private final List<FeatureFactory> realFeatures = BuiltIns.features(name -> Optional.empty(), () -> LootTables.NONE, name -> Optional.empty(),
            () -> TerrainStyles.NONE, Runnable::run, message -> { }, Audience::empty);
    private final IdRegistry<FeatureFactory> features = new IdRegistry<>(FeatureFactory::id);
    private final IdRegistry<StatGroup> statGroups = new IdRegistry<>(StatGroup::id);
    private final GameLoader loader;
    private final Map<String, YamlConfiguration> files = new HashMap<>();
    private final YamlConfiguration messages;

    BundledGamesTest() throws Exception {
        BuiltIns.gametypes(material -> true).forEach(gametypes::register);
        for (var feature : realFeatures) {
            // Features check block, item and effect names against the server's registry, which tests don't have,
            // so they are made without reading their settings here; their settings are checked when the server loads them.
            features.register(new FeatureFactory() {
                @Override
                public String id() {
                    return feature.id();
                }

                @Override
                public List<Setting<?>> settings() {
                    return feature.settings();
                }

                @Override
                public Feature create(ConfigReader config) {
                    config.keys();
                    return context -> { };
                }
            });
        }
        BuiltIns.statGroups().forEach(statGroups::register);
        messages = resource("messages/en.yml");
        loader = new GameLoader(gametypes, features, statGroups, Hosting.checks(true, () -> LootTables.NONE),
                () -> messages.getKeys(true).stream().filter(messages::isString).toList()).strict();
        for (var file : MiniGamiPlugin.EXAMPLE_GAMES) {
            files.put(file.replace(".yml", ""), resource("games/" + file));
        }
    }

    @Test
    void everyExampleGameLoadsOnTheFirstStart() throws Exception {
        var arenas = new IdRegistry<Arena>(Arena::id);
        arenas.register(new Arena("example", null, List.of(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty()));

        var loaded = loader.load(new YamlFiles("games", files, List.of()), arenas, false, resource("config.yml").getConfigurationSection("hud"), messages::isString);

        assertEquals(List.of(), loaded.problems());
        assertEquals(MiniGamiPlugin.EXAMPLE_GAMES.size(), loaded.entries().ids().size());
    }

    @Test
    void everyKeyOfTheExampleGamesIsOneTheirGameTypeDeclares() {
        var undeclared = new ArrayList<String>();
        files.forEach((id, file) -> {
            var reader = ConfigReader.strict("games/" + id + ".yml", file, loader.settings(file.getString("gametype", "")));
            for (String key : file.getKeys(true)) {
                try {
                    reader.suggest(key, List.of());
                } catch (IllegalStateException e) {
                    undeclared.add(id + ": " + key);
                }
            }
        });

        assertEquals(List.of(), undeclared);
    }

    @Test
    void everyGameTypeAndFeatureReadsOnlyTheSettingsItDeclares() {
        var undeclared = new ArrayList<String>();
        for (var id : gametypes.ids()) {
            var game = gametypes.find(id).orElseThrow();
            readStrictly(id, game.settings(), game::configure, undeclared);
        }
        for (var feature : realFeatures) {
            readStrictly(feature.id(), feature.settings(), feature::create, undeclared);
        }

        assertEquals(List.of(), undeclared);
    }

    /** Lets it read an empty section, which takes every fallback; a read of a key it doesn't declare fails at once. */
    private static void readStrictly(String id, List<Setting<?>> declared, Consumer<ConfigReader> read, List<String> undeclared) {
        try {
            read.accept(ConfigReader.strict(id, new MemoryConfiguration(), declared));
        } catch (IllegalStateException e) {
            undeclared.add(id + ": " + e.getMessage());
        } catch (RuntimeException | LinkageError e) {
            // Needs the server's registries, which tests don't have; its keys are checked through the example games.
        }
    }
}
