package io.github.tst_038.minigami.features.givedrops;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/** {@code give-drops: { snow_block: [snowball] }}: per block, the items its drops are replaced with. */
public final class GiveDropsFactory implements FeatureFactory {

    @Override
    public String id() {
        return "give-drops";
    }

    @Override
    public Feature create(ConfigReader config) {
        var drops = new HashMap<Material, List<Material>>();
        for (String name : config.keys()) {
            var items = config.materials(name);
            items.stream().filter(item -> !item.isItem())
                    .forEach(item -> config.problem(name, "'" + item.getKey().getKey() + "' is not an item"));
            var block = Material.matchMaterial(name);
            if (block == null || !block.isBlock()) {
                config.problem(name, "'" + name + "' is not a block");
            } else {
                drops.put(block, items);
            }
        }
        return new GiveDrops(drops);
    }
}
