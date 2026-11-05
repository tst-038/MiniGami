package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Region;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.stat.Tally;
import io.github.tst_038.minigami.core.win.LastManStanding;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.Test;

class PhaseFeaturesTest {

    private final List<String> log = new ArrayList<>();

    /** Logs when it is enabled and when the phase releases what it set up. */
    private Feature feature(String name) {
        return context -> {
            log.add(name + " enabled");
            context.scope().register(() -> log.add(name + " released"));
        };
    }

    private Match match(Map<String, List<Feature>> featuresByPhase) {
        var features = new PhaseFeatures(featuresByPhase, (phase, scope) -> new FakeContext(scope));
        return new Match(
                List.of(Phase.untimed("waiting"), Phase.untimed("playing"), Phase.untimed("ending")),
                new LastManStanding(),
                features);
    }

    @Test
    void enablesOnlyTheFeaturesOfTheEnteredPhase() {
        var match = match(Map.of("playing", List.of(feature("void-kills"), feature("no-hunger"))));

        match.start();
        assertTrue(log.isEmpty());

        match.advancePhase();
        assertEquals(List.of("void-kills enabled", "no-hunger enabled"), log);
    }

    @Test
    void leavingThePhaseReleasesWhatItsFeaturesSetUp() {
        var match = match(Map.of("playing", List.of(feature("void-kills"))));
        match.start();
        match.advancePhase();

        match.advancePhase();

        assertEquals(List.of("void-kills enabled", "void-kills released"), log);
    }

    @Test
    void featureInSeveralPhasesIsEnabledOncePerPhase() {
        var shared = feature("spectator");
        var match = match(Map.of("playing", List.of(shared), "ending", List.of(shared)));
        match.start();

        match.advancePhase();
        match.advancePhase();

        assertEquals(List.of("spectator enabled", "spectator released", "spectator enabled"), log);
    }

    @Test
    void failingFeatureDoesNotStopTheOthers() {
        Feature broken = context -> { throw new IllegalArgumentException("boom"); };
        var match = match(Map.of("waiting", List.of(broken, feature("lobby"))));

        var thrown = assertThrows(IllegalStateException.class, match::start);

        assertEquals("boom", thrown.getCause().getMessage());
        assertEquals(List.of("lobby enabled"), log);
        assertFalse(match.isFinished());
    }

    private record FakeContext(Scope scope) implements MatchContext {

        @Override
        public <E extends Event> void on(Class<E> type, Consumer<? super E> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MatchClock clock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Region> region(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Phase phase() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Roster roster() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void eliminate(Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Player> player(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<MatchResult> result() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Messages messages() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Audience audience() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scores scores() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addPoints(UUID id, int points) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tally stats() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void count(UUID id, Stat stat, long amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void endPhase() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> fill(List<Fill> fills) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> place(CompletableFuture<List<io.github.tst_038.minigami.api.region.Grid>> grids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Entity> T spawnFor(Collection<? extends Player> viewers, Location at, Class<T> type, Consumer<? super T> setup) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replace(Region region) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Region> regions(String group) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<io.github.tst_038.minigami.api.region.TeamRegion> teamRegions() {
            throw new UnsupportedOperationException();
        }
    }
}
