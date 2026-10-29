package io.github.tst_038.minigami.paper.party;

import io.github.tst_038.minigami.core.party.Parties.Party;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps the parties in storage. Every call returns at once; the work happens on the storage thread, in the order asked for. */
final class PartyStore {

    private final Storage storage;
    private final Logger logger;

    PartyStore(Storage storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    /** Completes on the storage thread, so hop to the server thread before using the parties. */
    CompletableFuture<List<Party>> loadAll() {
        return storage.submit(connection -> {
            var members = new LinkedHashMap<UUID, List<UUID>>();
            var leaders = new LinkedHashMap<UUID, UUID>();
            try (var select = connection.createStatement(); var rows = select.executeQuery("SELECT id, leader FROM parties")) {
                while (rows.next()) {
                    leaders.put(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)));
                }
            }
            try (var select = connection.createStatement(); var rows = select.executeQuery("SELECT party_id, player FROM party_members ORDER BY position")) {
                while (rows.next()) {
                    members.computeIfAbsent(UUID.fromString(rows.getString(1)), id -> new ArrayList<>()).add(UUID.fromString(rows.getString(2)));
                }
            }
            var parties = new ArrayList<Party>();
            leaders.forEach((id, leader) -> parties.add(new Party(id, leader, members.getOrDefault(id, List.of()))));
            return parties;
        });
    }

    /** Writes the party as it is now, replacing what was stored of it. */
    CompletableFuture<Void> save(Party party) {
        return storage.<Void>submit(connection -> {
            delete(connection, party.id());
            try (var insert = connection.prepareStatement("INSERT INTO parties (id, leader) VALUES (?, ?)")) {
                insert.setString(1, party.id().toString());
                insert.setString(2, party.leader().toString());
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("INSERT INTO party_members (player, party_id, position) VALUES (?, ?, ?)")) {
                for (int position = 0; position < party.members().size(); position++) {
                    insert.setString(1, party.members().get(position).toString());
                    insert.setString(2, party.id().toString());
                    insert.setInt(3, position);
                    insert.executeUpdate();
                }
            }
            return null;
        }).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not save party " + party.id(), failure);
            return null;
        });
    }

    CompletableFuture<Void> delete(UUID id) {
        return storage.<Void>submit(connection -> {
            delete(connection, id);
            return null;
        }).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not delete party " + id, failure);
            return null;
        });
    }

    private static void delete(Connection connection, UUID id) throws SQLException {
        try (var delete = connection.prepareStatement("DELETE FROM party_members WHERE party_id = ?")) {
            delete.setString(1, id.toString());
            delete.executeUpdate();
        }
        try (var delete = connection.prepareStatement("DELETE FROM parties WHERE id = ?")) {
            delete.setString(1, id.toString());
            delete.executeUpdate();
        }
    }
}
