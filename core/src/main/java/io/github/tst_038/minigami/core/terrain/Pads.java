package io.github.tst_038.minigami.core.terrain;

import java.util.List;

/** Questions about the flat ground around anchors, shared by the stages that must keep clear of it. */
final class Pads {

    private Pads() {
    }

    /** Whether (x, z) lies within {@code radius} blocks of any anchor. */
    static boolean near(List<Anchor> anchors, int x, int z, double radius) {
        for (var anchor : anchors) {
            if (Math.hypot(x - anchor.x(), z - anchor.z()) <= radius) {
                return true;
            }
        }
        return false;
    }
}
