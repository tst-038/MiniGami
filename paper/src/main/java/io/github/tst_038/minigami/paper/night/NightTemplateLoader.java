package io.github.tst_038.minigami.paper.night;

import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.gamenight.template.NightTemplate;
import io.github.tst_038.minigami.gamenight.template.NightTemplates;
import io.github.tst_038.minigami.paper.game.Game;
import java.util.Optional;

/** Turns {@code gamenights/<id>.yml} files into game night templates, checked against the games loaded with them. */
public final class NightTemplateLoader {

    public static final String FOLDER = "gamenights";

    private NightTemplateLoader() {
    }

    /** @param games the games loaded alongside, which the playlists may use */
    public static YamlFiles.Loaded<NightTemplate> load(YamlFiles files, IdRegistry<Game> games) {
        return files.parse(NightTemplate::id, (id, config) -> NightTemplates.read(id, config, game -> games.find(game).isPresent(),
                (game, variant) -> games.find(game).flatMap(found -> found.variant(Optional.of(variant))).isPresent()));
    }
}
