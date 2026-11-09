package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;

/** Creates a {@link Feature} from its section in a game's config, e.g. {@code void-kills: { y: 0 }}. */
public interface FeatureFactory {

    /** The key that selects this feature in config, e.g. {@code "void-kills"}. */
    String id();

    /**
     * Reads the feature's settings. Problems go to the reader; the returned feature is only used if
     * the whole file turned out valid.
     */
    Feature create(ConfigReader config);

    /**
     * Every key {@link #create} reads, so commands can complete and explain them without making the feature.
     * Empty for a feature that doesn't declare its settings yet.
     */
    default List<Setting<?>> settings() {
        return List.of();
    }
}
