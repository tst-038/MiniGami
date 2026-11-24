package io.github.tst_038.minigami.koth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class KothConfigLoaderTest {

    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "koth";
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    private final WorldLookup worlds = name -> name.equals("koth") ? Optional.of(world) : Optional.empty();

    @Test
    void theBundledConfigLoadsWithNoProblems() {
        var loaded = KothConfigLoader.load(Yaml.bundledConfig(), worlds);

        assertTrue(loaded.config().isPresent());
        assertEquals(List.of(), loaded.problems());
    }

    @Test
    void aFileWithProblemsLoadsNoConfigAndListsThem() {
        var loaded = KothConfigLoader.load(Yaml.bundledConfig() + "pointt: 1\n", worlds);

        assertTrue(loaded.config().isEmpty());
        assertEquals(List.of("config.yml: pointt: unknown key"), loaded.problems().stream().map(Object::toString).toList());
    }

    @Test
    void textThatIsNotYamlIsAProblemNotAnException() {
        var loaded = KothConfigLoader.load("a: [", worlds);

        assertTrue(loaded.config().isEmpty());
        assertEquals(1, loaded.problems().size());
    }
}
