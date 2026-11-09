package io.github.tst_038.minigami.features.loot;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.loot.LootTables;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * {@code loot: { style: christmas }} fills the loot containers the arena sets up. A style is a folder in {@code loot/}
 * whose tables take the place of the plain ones of the same name; without one the plain tables are used.
 */
public final class LootFactory implements FeatureFactory {

    private static final Setting<String> STYLE = Setting.of("style", Kind.text(),
            "the loot style, a folder in loot/ whose tables replace the plain ones; plain tables when left out").orElse("");

    private final Supplier<LootTables> tables;
    private final Function<String, Optional<ItemStack>> saved;

    /**
     * @param tables the tables as currently loaded; a game keeps the ones it was loaded with
     * @param saved finds an item saved with {@code /mga loot save} by name
     */
    public LootFactory(Supplier<LootTables> tables, Function<String, Optional<ItemStack>> saved) {
        this.tables = tables;
        this.saved = saved;
    }

    @Override
    public String id() {
        return "loot";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(STYLE);
    }

    @Override
    public Feature create(ConfigReader config) {
        var loaded = tables.get();
        var style = config.has("style") ? Optional.of(config.read(STYLE)) : Optional.<String>empty();
        style.filter(name -> !loaded.hasStyle(name)).ifPresent(name -> config.problem("style",
                "no loot style '" + name + "'; a style is a folder of tables in loot/" + (loaded.styles().isEmpty() ? ", and there is none yet" : ": " + loaded.styles())));
        return new Loot(loaded, style, saved);
    }
}
