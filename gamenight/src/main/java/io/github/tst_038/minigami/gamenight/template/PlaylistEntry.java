package io.github.tst_038.minigami.gamenight.template;

import java.util.Optional;

/** One game of the playlist, with the points it is worth (the night's, with this entry's changes laid over). */
public record PlaylistEntry(String game, Optional<String> variant, Points points) {
}
