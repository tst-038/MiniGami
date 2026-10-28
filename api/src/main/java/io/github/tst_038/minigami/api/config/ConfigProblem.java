package io.github.tst_038.minigami.api.config;

/**
 * One thing wrong in a config file.
 *
 * @param file the file, relative to the plugin folder, e.g. {@code games/spleef.yml}
 * @param key where in the file, e.g. {@code spawns[2].world}; empty for the file as a whole
 */
public record ConfigProblem(String file, String key, String message) {

    @Override
    public String toString() {
        return key.isEmpty() ? file + ": " + message : file + ": " + key + ": " + message;
    }
}
