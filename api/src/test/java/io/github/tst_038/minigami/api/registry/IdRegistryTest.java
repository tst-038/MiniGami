package io.github.tst_038.minigami.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class IdRegistryTest {

    private final IdRegistry<String> registry = new IdRegistry<>(Function.identity());

    @Test
    void findsRegisteredEntriesAndListsIdsSorted() {
        registry.register("void-kills");
        registry.register("kits");

        assertEquals(Optional.of("kits"), registry.find("kits"));
        assertEquals(Optional.empty(), registry.find("nope"));
        assertEquals(List.of("kits", "void-kills"), List.copyOf(registry.ids()));
    }

    @Test
    void rejectsDuplicateIds() {
        registry.register("kits");

        assertThrows(IllegalArgumentException.class, () -> registry.register("kits"));
    }

    @Test
    void rejectsIdsAdminsCouldNotType() {
        for (String id : List.of("", "Void-Kills", "void kills", "void_kills", "-kits", "kits-")) {
            assertThrows(IllegalArgumentException.class, () -> registry.register(id), id);
        }
    }
}
