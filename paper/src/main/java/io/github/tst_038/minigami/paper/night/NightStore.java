package io.github.tst_038.minigami.paper.night;

import io.github.tst_038.minigami.gamenight.night.Night;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps the game nights that were played in storage. Every call returns at once; the work happens on the storage thread. */
public final class NightStore {

    /**
     * A night as it ended.
     *
     * @param template the id of the template it was played from
     * @param name the template's name then, in MiniMessage
     */
    record Played(UUID id, String template, String name, Instant start, Instant end, List<Night.Result> results) {

        Played {
            results = List.copyOf(results);
        }
    }

    private final Storage storage;
    private final Logger logger;

    public NightStore(Storage storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    CompletableFuture<Void> save(Played night) {
        return storage.<Void>submit(connection -> {
            try (var insert = connection.prepareStatement("INSERT INTO nights (id, template, name, started_at, ended_at) VALUES (?, ?, ?, ?, ?)")) {
                insert.setString(1, night.id().toString());
                insert.setString(2, night.template());
                insert.setString(3, night.name());
                insert.setLong(4, night.start().toEpochMilli());
                insert.setLong(5, night.end().toEpochMilli());
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("INSERT INTO night_players (night_id, player, team, points, place) VALUES (?, ?, ?, ?, ?)")) {
                for (var result : night.results()) {
                    insert.setString(1, night.id().toString());
                    insert.setString(2, result.player().toString());
                    insert.setString(3, result.team().orElse(null));
                    insert.setInt(4, result.points());
                    insert.setInt(5, result.place());
                    insert.executeUpdate();
                }
            }
            return null;
        }).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not save game night " + night.id(), failure);
            return null;
        });
    }
}
