package io.github.tst_038.minigami.gamenight.template;

import java.util.List;

/**
 * A game night as written by an admin.
 *
 * @param name MiniMessage text
 * @param points the night's default; each playlist entry already carries its own
 */
public record NightTemplate(String id, String name, Teams teams, Points points, List<PlaylistEntry> playlist) {

    public NightTemplate {
        playlist = List.copyOf(playlist);
    }
}
