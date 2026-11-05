package io.github.tst_038.minigami.core.terrain;

import java.util.List;

/**
 * One area to generate: its size and where players will stand. Anchors are in the area's own
 * coordinates, y being the feet.
 */
public record AreaRequest(String id, int sizeX, int sizeY, int sizeZ, List<Anchor> anchors) {

    public AreaRequest {
        if (id.isBlank()) {
            throw new IllegalArgumentException("an area needs an id");
        }
        anchors = List.copyOf(anchors);
    }
}
