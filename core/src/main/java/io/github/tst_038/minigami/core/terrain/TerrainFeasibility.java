package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Catches setups that no amount of retrying can satisfy, before a match is spent finding out:
 * quota keys the style cannot produce, amounts that cannot fit, ores above the area, anchors
 * under water or outside the area.
 */
public final class TerrainFeasibility {

    private TerrainFeasibility() {
    }

    public static List<QuotaProblem> check(TerrainStyle style, Quota quota, List<AreaRequest> areas) {
        var problems = new ArrayList<QuotaProblem>();
        for (var entry : quota.ranges().entrySet()) {
            var key = entry.getKey();
            if (QuotaKeys.resolve(style, key).isEmpty()) {
                problems.add(new QuotaProblem("unknown-quota-key", Map.of("key", key, "style", style.id())));
            }
        }
        for (var area : areas) {
            checkTrees(style, quota, area, problems);
            checkOres(style, quota, area, problems);
            checkAnchors(style, area, problems);
        }
        return problems;
    }

    private static void checkTrees(TerrainStyle style, Quota quota, AreaRequest area, List<QuotaProblem> problems) {
        int room = (area.sizeX() / TreePlacer.SPACING) * (area.sizeZ() / TreePlacer.SPACING);
        for (var tree : style.trees()) {
            var range = quota.ranges().get(tree.name());
            if (range != null && range.min() > room) {
                problems.add(new QuotaProblem("quota-too-large", Map.of("key", tree.name(), "area", area.id(),
                        "wanted", String.valueOf(range.min()), "room", String.valueOf(room))));
            }
        }
    }

    private static void checkOres(TerrainStyle style, Quota quota, AreaRequest area, List<QuotaProblem> problems) {
        for (var ore : style.ores()) {
            var range = quota.ranges().get(ore.name());
            if (range != null && range.min() > 0 && ore.height().min() >= area.sizeY()) {
                problems.add(new QuotaProblem("ore-out-of-reach", Map.of("key", ore.name(), "area", area.id(),
                        "lowest", String.valueOf(ore.height().min()), "height", String.valueOf(area.sizeY()))));
            }
        }
    }

    private static void checkAnchors(TerrainStyle style, AreaRequest area, List<QuotaProblem> problems) {
        for (var anchor : area.anchors()) {
            boolean inside = anchor.x() >= 0 && anchor.x() < area.sizeX() && anchor.z() >= 0 && anchor.z() < area.sizeZ()
                    && anchor.y() >= 1 && anchor.y() < area.sizeY() - 3;
            if (!inside) {
                problems.add(new QuotaProblem("anchor-outside-area", Map.of("area", area.id(), "at", anchor.toString())));
            } else if (anchor.y() <= style.shape().seaLevel() + 1) {
                problems.add(new QuotaProblem("anchor-below-sea", Map.of("area", area.id(), "at", anchor.toString(),
                        "sea", String.valueOf(style.shape().seaLevel()))));
            }
        }
    }
}
