package io.github.tst_038.minigami.koth.round;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.tst_038.minigami.koth.hill.Dye;
import io.github.tst_038.minigami.koth.hill.Hill;
import io.github.tst_038.minigami.koth.hill.Scoring;
import io.github.tst_038.minigami.koth.stats.KothStats;
import io.github.tst_038.minigami.koth.stats.StatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RoundTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    /** A world where the listed players stand on the hill, and everyone in {@code here} may hold it. */
    private static final class FakeGround implements Ground {
        List<UUID> onPoint = List.of();
        final Set<UUID> here = new HashSet<>(Set.of(ALICE, BOB));
        final List<String> told = new ArrayList<>();
        final List<Dye> painted = new ArrayList<>();
        long credited;

        @Override
        public List<UUID> onPoint() {
            return onPoint;
        }

        @Override
        public boolean holderHere(UUID player) {
            return here.contains(player);
        }

        @Override
        public String nameOf(UUID player) {
            return player.equals(ALICE) ? "Alice" : "Bob";
        }

        @Override
        public void paint(Dye dye) {
            painted.add(dye);
        }

        @Override
        public void captured(String player) {
            told.add("captured " + player);
        }

        @Override
        public void dropped(String player) {
            told.add("dropped " + player);
        }

        @Override
        public void credit(UUID player, long points) {
            credited += points;
        }
    }

    private final FakeGround ground = new FakeGround();
    private final StatBuffer stats = new StatBuffer();
    private final Round round = new Round(new Hill(2, new Scoring(3, 1)), Dye.WHITE, ground, stats, RandomGenerator.getDefault());

    private void seconds(int count) {
        for (int i = 0; i < count; i++) {
            round.second();
        }
    }

    @Test
    void theHillIsPaintedNeutralAtTheStartAndTheEnd() {
        round.begin();
        round.end();

        assertEquals(List.of(Dye.WHITE, Dye.WHITE), ground.painted);
    }

    @Test
    void aCaptureIsToldPaintedAndCounted() {
        ground.onPoint = List.of(ALICE);
        seconds(2);

        assertEquals(Optional.of(ALICE), round.holder());
        assertEquals(Optional.of("Alice"), round.holderName());
        assertEquals(List.of("captured Alice"), ground.told);
        assertEquals(1, ground.painted.size());
        assertEquals(Map.of(ALICE, Map.of(KothStats.CAPTURES, 1L)), stats.drain());
    }

    @Test
    void theHolderEarnsPointsEverySecondAfterTheCapture() {
        ground.onPoint = List.of(ALICE);
        seconds(2);
        seconds(2);

        assertEquals(6, ground.credited);
        assertEquals(6L, stats.drain().get(ALICE).get(KothStats.POINTS));
    }

    @Test
    void aNewCaptureIsAnotherColour() {
        ground.onPoint = List.of(ALICE);
        seconds(2);
        ground.onPoint = List.of(BOB);
        seconds(2);

        assertEquals(Optional.of(BOB), round.holder());
        assertNotEquals(ground.painted.get(0), ground.painted.get(1));
    }

    @Test
    void aHolderWhoIsGoneDropsTheHillAndEarnsNothingMore() {
        ground.onPoint = List.of(ALICE);
        seconds(2);
        ground.here.remove(ALICE);
        ground.onPoint = List.of();
        seconds(3);

        assertEquals(Optional.empty(), round.holder());
        assertEquals(Optional.empty(), round.holderName());
        assertEquals(List.of("captured Alice", "dropped Alice"), ground.told);
        assertEquals(0, ground.credited);
        assertEquals(2, ground.painted.size());
        assertEquals(Dye.WHITE, ground.painted.get(1));
    }
}
