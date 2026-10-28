package io.github.tst_038.minigami.paper.stats;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** A player's stored totals, per game and variant, as they were when it was loaded. */
public record Profile(Map<VariantId, Totals> byVariant) {

    /** @param variant empty for the game itself */
    public record VariantId(String game, Optional<String> variant) {
    }

    public Profile {
        byVariant = Map.copyOf(byVariant);
    }

    /** @param variant empty for the game itself */
    public Totals variant(String game, Optional<String> variant) {
        return byVariant.getOrDefault(new VariantId(game, variant), Totals.NONE);
    }

    /** The game's totals over all its variants. */
    public Totals game(String game) {
        return byVariant.entrySet().stream().filter(entry -> entry.getKey().game().equals(game))
                .map(Map.Entry::getValue).reduce(Totals.NONE, Totals::plus);
    }

    /** The games they played, by id. */
    public Set<String> games() {
        var games = new TreeSet<String>();
        byVariant.keySet().forEach(key -> games.add(key.game()));
        return games;
    }
}
