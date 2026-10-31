package io.github.tst_038.minigami.core.diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What the checks found about one game in one arena, in the order they found it. */
public final class Diagnosis {

    private final String game;
    private final String arena;
    private final List<Finding> findings = new ArrayList<>();

    private Diagnosis(String game, String arena) {
        this.game = game;
        this.arena = arena;
    }

    /** Runs every check on the subject, in order. */
    public static <S> Diagnosis run(String game, String arena, S subject, List<? extends Check<? super S>> checks) {
        var diagnosis = new Diagnosis(game, arena);
        checks.forEach(check -> check.check(subject, diagnosis));
        return diagnosis;
    }

    /** The arena can't host the game until this is fixed; {@code fix} is the command that does. */
    public void blocking(String code, Map<String, String> values, String fix) {
        add(code, Severity.BLOCKING, values, Optional.of(fix));
    }

    /** The arena can't host the game, and no command fixes it. */
    public void blocking(String code, Map<String, String> values) {
        add(code, Severity.BLOCKING, values, Optional.empty());
    }

    /** The arena hosts the game, but likely not as meant; {@code fix} is the command that helps. */
    public void warning(String code, Map<String, String> values, String fix) {
        add(code, Severity.WARNING, values, Optional.of(fix));
    }

    private void add(String code, Severity severity, Map<String, String> values, Optional<String> fix) {
        findings.add(new Finding(code, severity, game, arena, values, fix));
    }

    public List<Finding> findings() {
        return List.copyOf(findings);
    }

    /** Whether the arena can host the game: nothing blocking was found. */
    public boolean isReady() {
        return findings.stream().noneMatch(Finding::isBlocking);
    }
}
