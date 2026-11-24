package io.github.tst_038.minigami.koth.restriction;

import java.util.Map;
import java.util.Optional;

/** A start needs at least this many players online. */
public record MinOnline(int players) implements Restriction {

    public MinOnline {
        if (players < 1) {
            throw new IllegalArgumentException("at least one player must be needed, not " + players);
        }
    }

    @Override
    public Optional<Refusal> check(Situation situation) {
        if (situation.playersOnline() >= players) {
            return Optional.empty();
        }
        return Optional.of(new Refusal("restriction.min-online",
                Map.of("online", String.valueOf(situation.playersOnline()), "needed", String.valueOf(players))));
    }
}
