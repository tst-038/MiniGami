package io.github.tst_038.minigami.koth.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.schedule.Cron;
import io.github.tst_038.minigami.koth.restriction.MinOnline;
import io.github.tst_038.minigami.koth.restriction.Refusal;
import io.github.tst_038.minigami.koth.restriction.Situation;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivationsTest {

    private static final Situation CROWDED = new Situation(5);
    private static final Situation EMPTY = new Situation(1);
    private static final List<Duration> WARNINGS = List.of(Duration.ofMinutes(2), Duration.ofMinutes(60), Duration.ofMinutes(15));

    private static LocalDateTime at(int day, int hour, int minute, int second) {
        return LocalDateTime.of(2026, 3, day, hour, minute, second);
    }

    private static Window window(int day, int hour, int minute, int lengthMinutes) {
        var start = at(day, hour, minute, 0);
        return new Window(start, start.plusMinutes(lengthMinutes));
    }

    /** Daily at 20:00 for an hour, warned at 60, 15 and 2 minutes, needing three players. */
    private static Plan plan() {
        return plan(60, 3, "0 20 * * *");
    }

    private static Plan plan(int lengthMinutes, int needed, String... lines) {
        var starts = Arrays.stream(lines).map(Cron::parse).toList();
        return new Plan(new CronTimetable(starts, Duration.ofMinutes(lengthMinutes)), WARNINGS, List.of(new MinOnline(needed)));
    }

    private static Refusal notEnough(int online) {
        return new Refusal("restriction.min-online", Map.of("online", String.valueOf(online), "needed", "3"));
    }

    /** Ticks every second from just after {@code from} through {@code to}, keeping each event with its time. */
    private static List<Map.Entry<LocalDateTime, ActivationEvent>> run(Activations activations, LocalDateTime from, LocalDateTime to, Situation situation) {
        var seen = new ArrayList<Map.Entry<LocalDateTime, ActivationEvent>>();
        for (var now = from.plusSeconds(1); !now.isAfter(to); now = now.plusSeconds(1)) {
            for (var event : activations.tick(now, situation)) {
                seen.add(Map.entry(now, event));
            }
        }
        return seen;
    }

    @Test
    void aWindowStartsWhenItComesAndEndsWhenTimeIsUp() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));

        assertEquals(List.of(), activations.tick(at(2, 19, 59, 59), CROWDED));
        assertEquals(List.of(new ActivationEvent.Started(window(2, 20, 0, 60), false)), activations.tick(at(2, 20, 0, 0), CROWDED));
        assertEquals(Optional.of(window(2, 20, 0, 60)), activations.running());
        assertEquals(List.of(), activations.tick(at(2, 20, 59, 59), CROWDED));
        assertEquals(List.of(new ActivationEvent.Ended(window(2, 20, 0, 60), EndReason.TIME_UP)), activations.tick(at(2, 21, 0, 0), CROWDED));
        assertEquals(Optional.empty(), activations.running());
        assertEquals(List.of(), activations.tick(at(2, 21, 0, 1), CROWDED));
    }

    @Test
    void aTickThatComesLateStillStartsTheWindow() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));

        assertEquals(List.of(new ActivationEvent.Started(window(2, 20, 0, 60), false)), activations.tick(at(2, 20, 0, 5), CROWDED));
    }

    @Test
    void eachWarningComesExactlyOnceAtItsMoment() {
        var created = at(2, 18, 0, 0);
        var seen = run(new Activations(plan(), created), created, at(2, 21, 30, 0), CROWDED);

        var warnings = seen.stream().filter(entry -> entry.getValue() instanceof ActivationEvent.Warning).toList();
        assertEquals(List.of(
                Map.entry(at(2, 19, 0, 0), (ActivationEvent) new ActivationEvent.Warning(Duration.ofMinutes(60), at(2, 20, 0, 0))),
                Map.entry(at(2, 19, 45, 0), (ActivationEvent) new ActivationEvent.Warning(Duration.ofMinutes(15), at(2, 20, 0, 0))),
                Map.entry(at(2, 19, 58, 0), (ActivationEvent) new ActivationEvent.Warning(Duration.ofMinutes(2), at(2, 20, 0, 0)))),
                warnings);
        assertEquals(5, seen.size(), "the three warnings, the start and the end, and nothing else");
    }

    @Test
    void warningsDueBeforeTheStartWereCreatedAreNotGiven() {
        var created = at(2, 19, 50, 0);
        var activations = new Activations(plan(), created);

        assertEquals(List.of(), activations.tick(at(2, 19, 50, 1), CROWDED));
        var later = run(activations, at(2, 19, 50, 1), at(2, 19, 59, 0), CROWDED);

        assertEquals(List.of(Map.entry(at(2, 19, 58, 0), (ActivationEvent) new ActivationEvent.Warning(Duration.ofMinutes(2), at(2, 20, 0, 0)))), later);
    }

    @Test
    void aWarningDueExactlyWhenItWasCreatedIsNotGiven() {
        var activations = new Activations(plan(), at(2, 19, 45, 0));

        assertEquals(List.of(), activations.tick(at(2, 19, 45, 1), CROWDED));
    }

    @Test
    void noWarningIsGivenWhileItRunsOrAfterwardsForOneThatFellInsideIt() {
        // Windows 20:00-20:20 and 20:30-20:50: the 15 minute warning for the second falls at 20:15, inside the first.
        var plan = plan(20, 3, "0 20 * * *", "30 20 * * *");
        var created = at(2, 19, 0, 0);
        var seen = run(new Activations(plan, created), created, at(2, 21, 0, 0), CROWDED);

        var forSecond = seen.stream()
                .filter(entry -> entry.getValue() instanceof ActivationEvent.Warning warning && warning.start().equals(at(2, 20, 30, 0)))
                .toList();
        assertEquals(List.of(Map.entry(at(2, 20, 28, 0), (ActivationEvent) new ActivationEvent.Warning(Duration.ofMinutes(2), at(2, 20, 30, 0)))), forSecond);
        assertTrue(seen.stream().noneMatch(entry -> entry.getKey().isAfter(at(2, 20, 0, 0)) && entry.getKey().isBefore(at(2, 20, 20, 0))
                && entry.getValue() instanceof ActivationEvent.Warning), "none while the first window runs");
    }

    @Test
    void aRefusedWindowIsSkippedWithTheReason() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));

        assertEquals(List.of(new ActivationEvent.Skipped(window(2, 20, 0, 60), notEnough(1))), activations.tick(at(2, 20, 0, 0), EMPTY));
        assertEquals(Optional.empty(), activations.running());
    }

    @Test
    void aSkippedWindowIsNotRetriedWhenPlayersArrive() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));
        activations.tick(at(2, 20, 0, 0), EMPTY);

        assertEquals(List.of(), run(activations, at(2, 20, 0, 0), at(2, 21, 30, 0), CROWDED));
        assertEquals(Optional.empty(), activations.running());
    }

    @Test
    void aSkippedWindowIsNotResumedByTheNextActivations() {
        var dismissed = new ArrayList<LocalDateTime>();
        new Activations(plan(), at(2, 19, 59, 0), Optional.empty(), dismissed::add).tick(at(2, 20, 0, 0), EMPTY);
        assertEquals(List.of(at(2, 20, 0, 0)), dismissed);

        // A restart in the middle of the skipped window, as it would be handed what was remembered.
        var restarted = new Activations(plan(), at(2, 20, 30, 0), Optional.of(dismissed.getFirst()), start -> { });

        assertEquals(List.of(), restarted.tick(at(2, 20, 30, 1), CROWDED));
        assertEquals(Optional.empty(), restarted.running());
    }

    @Test
    void aStoppedWindowIsNotResumedByTheNextActivations() {
        var dismissed = new ArrayList<LocalDateTime>();
        var activations = new Activations(plan(), at(2, 19, 59, 0), Optional.empty(), dismissed::add);
        activations.tick(at(2, 20, 0, 0), CROWDED);
        activations.forceStop(at(2, 20, 10, 0));
        assertEquals(List.of(at(2, 20, 0, 0)), dismissed);

        var restarted = new Activations(plan(), at(2, 20, 30, 0), Optional.of(dismissed.getFirst()), start -> { });

        assertEquals(List.of(), restarted.tick(at(2, 20, 30, 1), CROWDED));
    }

    @Test
    void aWindowThatRanIsStillResumedByTheNextActivations() {
        var dismissed = new ArrayList<LocalDateTime>();
        new Activations(plan(), at(2, 19, 59, 0), Optional.empty(), dismissed::add).tick(at(2, 20, 0, 0), CROWDED);
        assertEquals(List.of(), dismissed);

        var restarted = new Activations(plan(), at(2, 20, 30, 0), Optional.empty(), dismissed::add);

        assertEquals(List.of(new ActivationEvent.Started(window(2, 20, 0, 60), false)), restarted.tick(at(2, 20, 30, 1), CROWDED));
    }

    @Test
    void theWindowAfterASkippedOneStartsNormally() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));
        activations.tick(at(2, 20, 0, 0), EMPTY);
        activations.tick(at(2, 21, 30, 0), CROWDED);

        assertEquals(List.of(new ActivationEvent.Started(window(3, 20, 0, 60), false)), activations.tick(at(3, 20, 0, 0), CROWDED));
    }

    @Test
    void aWindowStartingTheSecondItWasCreatedStillAsksTheRestrictions() {
        var activations = new Activations(plan(), at(2, 20, 0, 0));

        assertEquals(List.of(new ActivationEvent.Skipped(window(2, 20, 0, 60), notEnough(1))), activations.tick(at(2, 20, 0, 0), EMPTY));
    }

    @Test
    void aWindowAlreadyOpenWhenCreatedResumesWhateverTheRestrictionsSay() {
        var activations = new Activations(plan(), at(2, 20, 30, 0));

        assertEquals(List.of(new ActivationEvent.Started(window(2, 20, 0, 60), false)), activations.tick(at(2, 20, 30, 1), new Situation(0)));
        assertEquals(List.of(new ActivationEvent.Ended(window(2, 20, 0, 60), EndReason.TIME_UP)), activations.tick(at(2, 21, 0, 0), new Situation(0)));
    }

    @Test
    void aForcedStartRunsForTheTimetableLengthWhateverTheRestrictionsSay() {
        var activations = new Activations(plan(), at(2, 12, 0, 0));

        assertEquals(Optional.of(new ActivationEvent.Started(new Window(at(2, 12, 5, 0), at(2, 13, 5, 0)), true)),
                activations.forceStart(at(2, 12, 5, 0), Optional.empty()));
        assertEquals(List.of(), activations.tick(at(2, 12, 5, 1), EMPTY));
        assertEquals(List.of(new ActivationEvent.Ended(new Window(at(2, 12, 5, 0), at(2, 13, 5, 0)), EndReason.TIME_UP)),
                activations.tick(at(2, 13, 5, 0), EMPTY));
    }

    @Test
    void aForcedStartCanChooseItsLength() {
        var activations = new Activations(plan(), at(2, 12, 0, 0));

        assertEquals(Optional.of(new ActivationEvent.Started(new Window(at(2, 12, 0, 0), at(2, 12, 10, 0)), true)),
                activations.forceStart(at(2, 12, 0, 0), Optional.of(Duration.ofMinutes(10))));
        assertEquals(List.of(new ActivationEvent.Ended(new Window(at(2, 12, 0, 0), at(2, 12, 10, 0)), EndReason.TIME_UP)),
                activations.tick(at(2, 12, 10, 0), CROWDED));
    }

    @Test
    void forcingAStartWhileRunningDoesNothing() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));
        activations.tick(at(2, 20, 0, 0), CROWDED);

        assertEquals(Optional.empty(), activations.forceStart(at(2, 20, 10, 0), Optional.empty()));
        assertEquals(Optional.of(window(2, 20, 0, 60)), activations.running());
    }

    @Test
    void aForcedRunInsideAWindowTakesItsPlaceAndItIsNotStartedAgain() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));

        activations.forceStart(at(2, 20, 0, 0), Optional.of(Duration.ofMinutes(10)));

        assertEquals(List.of(new ActivationEvent.Ended(new Window(at(2, 20, 0, 0), at(2, 20, 10, 0)), EndReason.TIME_UP)),
                activations.tick(at(2, 20, 10, 0), CROWDED));
        assertEquals(List.of(), run(activations, at(2, 20, 10, 0), at(2, 21, 30, 0), CROWDED));
    }

    @Test
    void aScheduledWindowStillComesAfterAForcedRunEnded() {
        var activations = new Activations(plan(), at(2, 12, 0, 0));
        activations.forceStart(at(2, 12, 0, 0), Optional.of(Duration.ofMinutes(10)));
        activations.tick(at(2, 12, 10, 0), CROWDED);

        assertEquals(List.of(new ActivationEvent.Started(window(2, 20, 0, 60), false)), activations.tick(at(2, 20, 0, 0), CROWDED));
    }

    @Test
    void stoppingEndsTheRunAndTheSameWindowIsNotStartedAgain() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));
        activations.tick(at(2, 20, 0, 0), CROWDED);

        assertEquals(Optional.of(new ActivationEvent.Ended(window(2, 20, 0, 60), EndReason.STOPPED)), activations.forceStop(at(2, 20, 10, 0)));
        assertEquals(Optional.empty(), activations.running());
        assertEquals(List.of(), run(activations, at(2, 20, 10, 0), at(2, 21, 30, 0), CROWDED));
        assertEquals(List.of(new ActivationEvent.Started(window(3, 20, 0, 60), false)), activations.tick(at(3, 20, 0, 0), CROWDED));
    }

    @Test
    void stoppingAForcedRunLeavesTheNextWindowAlone() {
        var activations = new Activations(plan(), at(2, 12, 0, 0));
        activations.forceStart(at(2, 12, 0, 0), Optional.empty());

        assertEquals(Optional.of(new ActivationEvent.Ended(new Window(at(2, 12, 0, 0), at(2, 13, 0, 0)), EndReason.STOPPED)),
                activations.forceStop(at(2, 12, 30, 0)));
        assertEquals(List.of(new ActivationEvent.Started(window(2, 20, 0, 60), false)), activations.tick(at(2, 20, 0, 0), CROWDED));
    }

    @Test
    void stoppingWhileIdleDoesNothing() {
        assertEquals(Optional.empty(), new Activations(plan(), at(2, 12, 0, 0)).forceStop(at(2, 12, 0, 1)));
    }

    @Test
    void replanKeepsTheRunningWindowAndDoesNotRestartIt() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));
        activations.tick(at(2, 20, 0, 0), CROWDED);

        activations.replan(plan(10, 10, "0 3 * * *"));

        assertEquals(Optional.of(window(2, 20, 0, 60)), activations.running());
        assertEquals(List.of(), run(activations, at(2, 20, 0, 0), at(2, 20, 59, 59), new Situation(0)));
        assertEquals(List.of(new ActivationEvent.Ended(window(2, 20, 0, 60), EndReason.TIME_UP)), activations.tick(at(2, 21, 0, 0), CROWDED));
    }

    @Test
    void replanKeepsAHandledWindowHandled() {
        var activations = new Activations(plan(), at(2, 19, 59, 0));
        activations.tick(at(2, 20, 0, 0), EMPTY);

        activations.replan(plan(60, 1, "0 20 * * *"));

        assertEquals(List.of(), run(activations, at(2, 20, 0, 0), at(2, 21, 0, 0), CROWDED));
    }

    @Test
    void replanChangesWhatTheNextWindowIsAskedAndWhenItComes() {
        var activations = new Activations(plan(), at(2, 21, 30, 0));

        activations.replan(plan(30, 10, "0 22 * * *"));

        assertEquals(Optional.of(at(2, 22, 0, 0)), activations.nextStart(at(2, 21, 30, 0)));
        assertEquals(List.of(new ActivationEvent.Skipped(window(2, 22, 0, 30), new Refusal("restriction.min-online", Map.of("online", "5", "needed", "10")))),
                activations.tick(at(2, 22, 0, 0), CROWDED));
    }

    @Test
    void theNextStartComesFromTheTimetable() {
        var activations = new Activations(plan(), at(2, 12, 0, 0));

        assertEquals(Optional.of(at(2, 20, 0, 0)), activations.nextStart(at(2, 12, 0, 0)));
        assertEquals(Optional.of(at(3, 20, 0, 0)), activations.nextStart(at(2, 20, 0, 0)));
    }
}
