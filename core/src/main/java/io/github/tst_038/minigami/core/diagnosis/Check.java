package io.github.tst_038.minigami.core.diagnosis;

/**
 * Looks at one concern, such as whether an arena has spawns, and tells the diagnosis what is wrong.
 * Finding nothing means fine.
 *
 * @param <S> what it looks at
 */
@FunctionalInterface
public interface Check<S> {

    void check(S subject, Diagnosis diagnosis);
}
