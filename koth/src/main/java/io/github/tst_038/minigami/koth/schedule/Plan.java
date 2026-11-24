package io.github.tst_038.minigami.koth.schedule;

import io.github.tst_038.minigami.koth.restriction.Restriction;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Everything that decides when KOTH runs: the timetable, how long before a start to warn, and what must
 * hold for a start to go ahead. Warnings are kept longest first, each length once.
 */
public record Plan(Timetable timetable, List<Duration> warnings, List<Restriction> restrictions) {

    public Plan {
        for (var warning : warnings) {
            if (!warning.isPositive()) {
                throw new IllegalArgumentException("a warning must come before the start, not " + warning);
            }
        }
        warnings = warnings.stream().sorted(Comparator.reverseOrder()).distinct().toList();
        restrictions = List.copyOf(restrictions);
    }
}
