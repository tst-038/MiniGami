package io.github.tst_038.minigami.paper.stats;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** A player's stored totals, per game and mode, as they were when it was loaded. */
public record Profile(Map<ModeId, Totals> byMode) {

    /** A mode of a game; a mode's id is only unique together with its game's, since modes can be renamed and reused. */
    public record ModeId(String game, String mode) {
    }

    public Profile {
        byMode = Map.copyOf(byMode);
    }

    public Totals mode(String game, String mode) {
        return byMode.getOrDefault(new ModeId(game, mode), Totals.NONE);
    }

    /** The game's totals over all its modes. */
    public Totals game(String game) {
        return byMode.entrySet().stream().filter(entry -> entry.getKey().game().equals(game))
                .map(Map.Entry::getValue).reduce(Totals.NONE, Totals::plus);
    }

    /** The games they played, by id. */
    public Set<String> games() {
        var games = new TreeSet<String>();
        byMode.keySet().forEach(key -> games.add(key.game()));
        return games;
    }
}
