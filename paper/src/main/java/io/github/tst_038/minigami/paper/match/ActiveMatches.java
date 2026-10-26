package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.core.match.Match;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The matches running on this server. Ticks them once per server tick, and disposes each one as
 * soon as it has finished.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class ActiveMatches {

    private final Logger logger;
    private final List<Match> matches = new ArrayList<>();

    public ActiveMatches(Logger logger) {
        this.logger = logger;
    }

    public void add(Match match) {
        matches.add(match);
    }

    public List<Match> all() {
        return Collections.unmodifiableList(matches);
    }

    /** Ticks every match. A failing match is logged and keeps running; the others are unaffected. */
    public void tick() {
        // Iterate a copy: ticking a match may start or end matches.
        for (Match match : List.copyOf(matches)) {
            try {
                match.tick();
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "A match failed to tick", e);
            }
            if (match.isFinished()) {
                dispose(match);
            }
        }
    }

    /** Disposes every match, e.g. when the plugin shuts down. */
    public void disposeAll() {
        for (Match match : List.copyOf(matches)) {
            dispose(match);
        }
    }

    private void dispose(Match match) {
        matches.remove(match);
        try {
            match.dispose();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "A match failed to clean up", e);
        }
    }
}
