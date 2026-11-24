package io.github.tst_038.minigami.koth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.koth.schedule.CronTimetable;
import io.github.tst_038.minigami.koth.schedule.CycleTimetable;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimetableReaderTest {

    @Test
    void readsCronLinesAndTheLengthOfAWindow() {
        var reader = Yaml.reader("{ type: cron, when: ['0 20 * * 1', '0 18 * * 6'], length-minutes: 60 }");

        var timetable = assertInstanceOf(CronTimetable.class, TimetableReader.read(reader).orElseThrow());

        assertEquals(2, timetable.starts().size());
        assertEquals(Duration.ofMinutes(60), timetable.length());
    }

    @Test
    void oneCronLineNeedsNoList() {
        var reader = Yaml.reader("{ type: cron, when: '0 20 * * 1', length-minutes: 30 }");

        assertEquals(1, assertInstanceOf(CronTimetable.class, TimetableReader.read(reader).orElseThrow()).starts().size());
    }

    @Test
    void readsACycle() {
        var reader = Yaml.reader("{ type: cycle, active-minutes: 15, break-minutes: 5 }");

        assertEquals(new CycleTimetable(Duration.ofMinutes(15), Duration.ofMinutes(5)), TimetableReader.read(reader).orElseThrow());
    }

    @Test
    void aBadCronLineIsReportedUnderWhenWithItsText() {
        var reader = Yaml.reader("{ type: cron, when: ['0 20 * * 1', 'soon'], length-minutes: 60 }");

        var problems = Yaml.problems(reader, r -> assertTrue(TimetableReader.read(r).isPresent()));

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().startsWith("when: 'soon': "), problems.getFirst());
    }

    @Test
    void aCronTimetableWithoutAnyValidLineIsEmpty() {
        var reader = Yaml.reader("{ type: cron, when: [], length-minutes: 60 }");

        assertEquals(List.of("when: needs at least one cron line"),
                Yaml.problems(reader, r -> assertTrue(TimetableReader.read(r).isEmpty())));
    }

    @Test
    void aMissingWhenIsReportedOnce() {
        var reader = Yaml.reader("{ type: cron, length-minutes: 60 }");

        assertEquals(List.of("when: is missing"), Yaml.problems(reader, r -> assertTrue(TimetableReader.read(r).isEmpty())));
    }

    @Test
    void aLengthOutOfRangeIsAProblemNotAnException() {
        var reader = Yaml.reader("{ type: cycle, active-minutes: 0, break-minutes: 5 }");

        assertEquals(List.of("active-minutes: must be between 1 and 10080"),
                Yaml.problems(reader, r -> TimetableReader.read(r)));
    }

    @Test
    void keysOfTheOtherTypeAreUnknown() {
        var reader = Yaml.reader("{ type: cycle, active-minutes: 15, break-minutes: 5, length-minutes: 60 }");

        assertEquals(List.of("length-minutes: unknown key"), Yaml.problems(reader, r -> TimetableReader.read(r)));
    }

    @Test
    void warningsAreReadInMinutes() {
        var reader = Yaml.reader("warn-minutes: [60, 15, 2]");

        assertEquals(List.of(Duration.ofMinutes(60), Duration.ofMinutes(15), Duration.ofMinutes(2)), TimetableReader.warnings(reader));
    }

    @Test
    void warningsDefaultWhenLeftOut() {
        assertEquals(List.of(Duration.ofMinutes(60), Duration.ofMinutes(15), Duration.ofMinutes(2)),
                TimetableReader.warnings(Yaml.reader("{}")));
    }

    @Test
    void noWarningsIsAnEmptyList() {
        assertEquals(List.of(), TimetableReader.warnings(Yaml.reader("warn-minutes: []")));
    }

    @Test
    void aWarningThatIsNotANumberIsAProblem() {
        var reader = Yaml.reader("warn-minutes: [60, soon, 0]");

        var problems = Yaml.problems(reader, r -> assertEquals(List.of(Duration.ofMinutes(60)), TimetableReader.warnings(r)));

        assertEquals(List.of("warn-minutes: 'soon' must be a whole number of minutes from 1 to 10080",
                "warn-minutes: '0' must be a whole number of minutes from 1 to 10080"), problems);
    }
}
