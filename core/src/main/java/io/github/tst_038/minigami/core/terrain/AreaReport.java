package io.github.tst_038.minigami.core.terrain;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.Map;

/**
 * How one generated area measured up against the quota.
 *
 * @param attempts  how many times the area was generated (1 when the first try was good)
 * @param readings  per quota key, what was aimed for and what the kept area holds
 */
public record AreaReport(String area, int attempts, Map<String, Reading> readings) {

    public AreaReport {
        readings = Map.copyOf(readings);
    }

    public boolean satisfied() {
        return readings.values().stream().allMatch(Reading::met);
    }

    /** One key: the target for this match, the amount found, and the range that was accepted. */
    public record Reading(int target, int achieved, IntRange accepted) {

        public boolean met() {
            return achieved >= accepted.min() && achieved <= accepted.max();
        }
    }
}
