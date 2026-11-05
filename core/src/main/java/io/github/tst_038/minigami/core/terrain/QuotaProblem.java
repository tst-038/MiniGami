package io.github.tst_038.minigami.core.terrain;

import java.util.Map;

/**
 * Something that makes a quota or an anchor impossible or unwise for a style and area size.
 *
 * @param code   what is wrong, stable across versions (for example {@code unknown-quota-key})
 * @param values the details a message fills in
 */
public record QuotaProblem(String code, Map<String, String> values) {

    public QuotaProblem {
        values = Map.copyOf(values);
    }
}
