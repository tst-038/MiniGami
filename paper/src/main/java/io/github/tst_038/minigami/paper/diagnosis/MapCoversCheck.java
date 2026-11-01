package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.diagnosis.Check;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import java.util.Map;
import java.util.stream.Stream;

/** A game that changes blocks can only put back what the saved map covers, so every region it names must lie inside the map. */
final class MapCoversCheck implements Check<Hosting> {

    @Override
    public void check(Hosting hosting, Diagnosis diagnosis) {
        var map = hosting.arena().map();
        if (!hosting.changesBlocks() || map.isEmpty()) {
            return;
        }
        var regions = hosting.arena().regions();
        var named = Stream.of(
                        hosting.gametype().regions().stream(),
                        hosting.featureRegions().stream(),
                        hosting.gametype().regionPerTeam().stream().flatMap(prefix -> Stream.iterate(1, number -> number <= hosting.mostTeams(), number -> number + 1).map(number -> prefix + "-" + number)),
                        regions.keySet().stream().filter(id -> hosting.gametype().regionGroups().stream().anyMatch(group -> regions.get(id).isIn(group))))
                .flatMap(stream -> stream)
                .distinct()
                .filter(regions::containsKey);
        named.filter(id -> !inside(regions.get(id), map.get()))
                .forEach(id -> diagnosis.blocking("region-outside-map", Map.of("region", id), hosting.command("map save")));
    }

    private static boolean inside(Region region, Region map) {
        return region.minX() >= map.minX() && region.maxX() <= map.maxX() && region.minY() >= map.minY() && region.maxY() <= map.maxY()
                && region.minZ() >= map.minZ() && region.maxZ() <= map.maxZ();
    }
}
