package io.github.tst_038.minigami.api.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class RegionTest {

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private final World world = world();
    private final Region hill = Region.between("hill", world, 10, 64, -5, 0, 66, 5);

    @Test
    void cornersMayBeGivenInAnyOrder() {
        assertEquals(new Region("hill", world, 0, 64, -5, 10, 66, 5), hill);
    }

    @Test
    void containsTheBlocksFromCornerToCornerBothIncluded() {
        assertTrue(hill.contains(new Location(world, 0, 64, -5)));
        assertTrue(hill.contains(new Location(world, 10.9, 66.9, 5.9)));
        assertTrue(hill.contains(new Location(world, 5.5, 65, 0.5)));

        assertFalse(hill.contains(new Location(world, 11, 65, 0)));
        assertFalse(hill.contains(new Location(world, -0.1, 65, 0)));
        assertFalse(hill.contains(new Location(world, 5, 67, 0)));
        assertFalse(hill.contains(new Location(world, 5, 63.9, 0)));
    }

    @Test
    void onlyInItsOwnWorld() {
        assertFalse(hill.contains(new Location(world(), 5, 65, 0)));
    }

    @Test
    void theMinimumCornerMustComeFirst() {
        assertThrows(IllegalArgumentException.class, () -> new Region("hill", world, 1, 0, 0, 0, 0, 0));
    }
}
