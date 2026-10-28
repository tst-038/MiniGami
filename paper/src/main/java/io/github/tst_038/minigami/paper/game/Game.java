package io.github.tst_038.minigami.paper.game;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A game as players know it, such as Spleef: what {@code games/<id>.yml} sets up, playable as it
 * is, and its variants, each laid over it.
 *
 * @param base the game itself, without a variant
 * @param variants by id
 */
public record Game(String id, Variant base, Map<String, Variant> variants) {

    public Game {
        variants = new TreeMap<>(variants);
    }

    public String name() {
        return base.name();
    }

    /** @param variant empty for the game itself */
    public Optional<Variant> variant(Optional<String> variant) {
        return variant.isEmpty() ? Optional.of(base) : Optional.ofNullable(variants.get(variant.get()));
    }

    /** The game itself, then its variants by id. */
    public List<Variant> all() {
        return Stream.concat(Stream.of(base), variants.values().stream()).toList();
    }
}
