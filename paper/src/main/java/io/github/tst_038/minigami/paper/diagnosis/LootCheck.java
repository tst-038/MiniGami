package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.api.loot.LootPoint;
import io.github.tst_038.minigami.api.loot.LootTables;
import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * For a game that fills loot containers: that the arena has some, that each is still a container, and that the
 * tables they name exist.
 */
final class LootCheck implements Check<Hosting> {

    private final Supplier<LootTables> tables;
    private final Predicate<LootPoint> isContainer;

    /**
     * @param tables the tables as currently loaded
     * @param isContainer whether the block of a point still is a container, which needs the world
     */
    LootCheck(Supplier<LootTables> tables, Predicate<LootPoint> isContainer) {
        this.tables = tables;
        this.isContainer = isContainer;
    }

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        if (!hosting.usesLoot()) {
            return;
        }
        var points = hosting.arena().lootPoints();
        if (points.isEmpty()) {
            diagnosis.warning("no-loot-points", Map.of(), hosting.command("loot add <table>"));
        }
        var known = tables.get();
        for (var point : points) {
            var spot = Map.of("x", String.valueOf(point.x()), "y", String.valueOf(point.y()), "z", String.valueOf(point.z()));
            if (!isContainer.test(point)) {
                diagnosis.warning("loot-point-gone", spot, hosting.command("loot remove"));
            }
            point.tables().keySet().stream().filter(id -> !exists(known, id)).forEach(id ->
                    diagnosis.blocking("unknown-loot-table", Map.of("table", id, "x", spot.get("x"), "y", spot.get("y"), "z", spot.get("z"))));
        }
    }

    /** A table of a style alone, with no plain one of the same name, is found too. */
    private static boolean exists(LootTables tables, String id) {
        return tables.find(id).isPresent() || tables.styles().stream().anyMatch(style -> tables.find(style + "/" + id).isPresent());
    }
}
