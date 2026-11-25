package io.github.tst_038.minigami.core.time;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Lengths of time the way players read and type them: "2d 3h", "1h 5m", "4m 9s", "9s". */
public final class Durations {

    private static final Pattern WHOLE = Pattern.compile("(\\d{1,9}[smhdw])+");
    private static final Pattern PART = Pattern.compile("(\\d{1,9})([smhdw])");

    private Durations() {
    }

    /**
     * Rounds up to whole seconds; zero and negative lengths read "0s". Shows the two largest units of days, hours, minutes and
     * seconds that follow each other, leaving the second out when it is zero: "2d 3h", "1h", "4m 9s".
     */
    public static String format(Duration length) {
        if (!length.isPositive()) {
            return "0s";
        }
        long seconds = length.plusNanos(999_999_999).getSeconds();
        long[] amounts = {seconds / 86_400, seconds % 86_400 / 3600, seconds % 3600 / 60, seconds % 60};
        String[] units = {"d", "h", "m", "s"};
        int first = 0;
        while (amounts[first] == 0) {
            first++;
        }
        var text = amounts[first] + units[first];
        return first < 3 && amounts[first + 1] > 0 ? text + " " + amounts[first + 1] + units[first + 1] : text;
    }

    /**
     * The length typed as amounts with a unit each, {@code s}, {@code m}, {@code h}, {@code d} or {@code w}, without spaces:
     * "30m", "2h", "7d", "1h30m". Empty if the text is anything else or adds up to zero.
     */
    public static Optional<Duration> parse(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        if (!WHOLE.matcher(lower).matches()) {
            return Optional.empty();
        }
        var total = Duration.ZERO;
        var parts = PART.matcher(lower);
        while (parts.find()) {
            long amount = Long.parseLong(parts.group(1));
            total = total.plus(switch (parts.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> Duration.ofDays(amount * 7);
            });
        }
        return Optional.of(total).filter(Duration::isPositive);
    }
}
