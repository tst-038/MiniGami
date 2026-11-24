package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.koth.hill.Dye;
import java.util.List;
import java.util.UUID;

/** What a {@link Round} sees and does in the world, so its rules need no server. Server thread only. */
interface Ground {

    /** The players who count as standing on the hill right now, each once. */
    List<UUID> onPoint();

    /** Whether the player is still where holding the hill is allowed: online, in the KOTH world and not in a match. */
    boolean holderHere(UUID player);

    String nameOf(UUID player);

    /** Colours the dyed blocks of the hill. */
    void paint(Dye dye);

    /** Tells the world that the player took the hill. */
    void captured(String player);

    /** Tells the world that the hill is free because its holder is gone. */
    void dropped(String player);

    /** Gives the player spendable points. */
    void credit(UUID player, long points);
}
