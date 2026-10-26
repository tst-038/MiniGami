package io.github.tst_038.minigami.api.feature;

import org.bukkit.configuration.ConfigurationSection;

/** Creates a {@link Feature} from its section in a mode's config, e.g. {@code void-kills: { y: 0 }}. */
public interface FeatureFactory {

    /** The key that selects this feature in config, e.g. {@code "void-kills"}. */
    String id();

    /** @throws IllegalArgumentException if the config is invalid, with a message naming the bad value */
    Feature create(ConfigurationSection config);
}
