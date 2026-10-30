package io.github.tst_038.minigami.paper.reward;

import io.github.tst_038.minigami.api.config.ConfigReader;

/**
 * A kind of reward, read from a reward entry that has its {@link #key()}, such as
 * {@code { chance: 40, item: diamond, amount: 3 }} for the {@code item} type.
 */
public interface RewardType {

    /** The key that marks an entry as this type, which holds its main value. */
    String key();

    /** Reads the entry's own keys; problems go to the reader, which may return any reward then. */
    Reward read(ConfigReader entry);
}
