package io.github.tst_038.minigami.api.config;

import java.util.List;
import java.util.stream.Collectors;

/** A config file has problems; lists all of them, not just the first. */
public final class InvalidConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    private final transient List<ConfigProblem> problems;

    public InvalidConfigException(List<ConfigProblem> problems) {
        super(problems.stream().map(ConfigProblem::toString).collect(Collectors.joining("\n")));
        this.problems = List.copyOf(problems);
    }

    public List<ConfigProblem> problems() {
        return problems;
    }
}
