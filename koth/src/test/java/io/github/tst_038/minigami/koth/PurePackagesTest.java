package io.github.tst_038.minigami.koth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The packages that hold KOTH's rules must stay free of Bukkit, so they can be tested without a server. */
class PurePackagesTest {

    private static final List<String> PURE = List.of("text", "schedule", "restriction", "hill", "ledger", "stats", "status");
    private static final List<String> FORBIDDEN = List.of("org.bukkit", "io.papermc", "net.kyori", "me.clip", "io.github.tst_038.minigami.api");

    @Test
    void purePackagesImportNothingPlatformSpecific() throws IOException {
        Path root = Path.of("src/main/java/io/github/tst_038/minigami/koth");
        List<String> offences = new ArrayList<>();
        for (String name : PURE) {
            Path folder = root.resolve(name);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file)) {
                        String text = line.strip();
                        if (FORBIDDEN.stream().anyMatch(prefix -> text.startsWith("import " + prefix))) {
                            offences.add(file + ": " + text);
                        }
                    }
                }
            }
        }
        assertTrue(offences.isEmpty(), "Pure packages import platform code: " + offences);
    }
}
