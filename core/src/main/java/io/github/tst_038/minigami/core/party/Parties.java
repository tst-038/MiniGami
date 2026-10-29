package io.github.tst_038.minigami.core.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Who is in which party. A player is in at most one party; its leader is the only one who invites,
 * kicks and disbands. A party that drops below two members through someone leaving or being
 * kicked is disbanded, as nobody is left to play with. Invites are only remembered here; making
 * them expire is up to the caller, with {@link #expire}.
 */
public final class Parties {

    /** What happened to a request; {@link #OK} is the only one that changed anything. */
    public enum Result {
        OK, NOT_IN_PARTY, NOT_LEADER, IN_PARTY, NO_INVITE, FULL, SELF
    }

    /** A snapshot: {@code members} is in the order they joined, the leader among them. */
    public record Party(UUID id, UUID leader, List<UUID> members) {

        public Party {
            members = List.copyOf(members);
        }
    }

    private record Invite(UUID leader, UUID invitee) {
    }

    private static final class Live {
        final UUID id;
        UUID leader;
        final List<UUID> members = new ArrayList<>();

        Live(UUID id, UUID leader) {
            this.id = id;
            this.leader = leader;
        }

        Party snapshot() {
            return new Party(id, leader, members);
        }
    }

    /** Asked each time, so a reloaded setting counts at once. */
    private final IntSupplier maxSize;
    private final Map<UUID, Live> byId = new HashMap<>();
    private final Map<UUID, Live> byPlayer = new HashMap<>();
    private final Set<Invite> invites = new HashSet<>();

    public Parties(IntSupplier maxSize) {
        this.maxSize = maxSize;
    }

    public Optional<Party> of(UUID player) {
        return Optional.ofNullable(byPlayer.get(player)).map(Live::snapshot);
    }

    public List<Party> all() {
        return byId.values().stream().map(Live::snapshot).toList();
    }

    /** Puts a party back as it was saved. Members already in a party are left out, and with nobody left there is no party. */
    public Optional<Party> restore(UUID id, UUID leader, List<UUID> members) {
        var free = members.stream().filter(member -> !byPlayer.containsKey(member)).distinct().toList();
        if (free.isEmpty() || byId.containsKey(id)) {
            return Optional.empty();
        }
        var party = new Live(id, free.contains(leader) ? leader : free.getFirst());
        party.members.addAll(free);
        byId.put(id, party);
        free.forEach(member -> byPlayer.put(member, party));
        return Optional.of(party.snapshot());
    }

    /** Invites the player to the leader's party, which the leader gets if they have none yet. */
    public Result invite(UUID leader, UUID invitee) {
        if (leader.equals(invitee)) {
            return Result.SELF;
        }
        var party = byPlayer.get(leader);
        if (party != null && !party.leader.equals(leader)) {
            return Result.NOT_LEADER;
        }
        if (byPlayer.containsKey(invitee)) {
            return Result.IN_PARTY;
        }
        if (party != null && party.members.size() >= maxSize.getAsInt()) {
            return Result.FULL;
        }
        if (party == null) {
            party = new Live(UUID.randomUUID(), leader);
            party.members.add(leader);
            byId.put(party.id, party);
            byPlayer.put(leader, party);
        }
        invites.add(new Invite(leader, invitee));
        return Result.OK;
    }

    /** Forgets an invite that ran out; whether it was still open. */
    public boolean expire(UUID leader, UUID invitee) {
        return invites.remove(new Invite(leader, invitee));
    }

    public Result accept(UUID invitee, UUID leader) {
        if (byPlayer.containsKey(invitee)) {
            return Result.IN_PARTY;
        }
        var party = byPlayer.get(leader);
        // The leader may have handed over or disbanded since the invite.
        if (!invites.contains(new Invite(leader, invitee)) || party == null || !party.leader.equals(leader)) {
            return Result.NO_INVITE;
        }
        if (party.members.size() >= maxSize.getAsInt()) {
            return Result.FULL;
        }
        invites.remove(new Invite(leader, invitee));
        party.members.add(invitee);
        byPlayer.put(invitee, party);
        return Result.OK;
    }

    /** A leader that leaves hands the party to the member who has been in it longest. */
    public Result leave(UUID player) {
        var party = byPlayer.get(player);
        if (party == null) {
            return Result.NOT_IN_PARTY;
        }
        remove(party, player);
        return Result.OK;
    }

    public Result kick(UUID leader, UUID member) {
        var party = byPlayer.get(leader);
        if (party == null) {
            return Result.NOT_IN_PARTY;
        }
        if (!party.leader.equals(leader)) {
            return Result.NOT_LEADER;
        }
        if (leader.equals(member)) {
            return Result.SELF;
        }
        if (byPlayer.get(member) != party) {
            return Result.NOT_IN_PARTY;
        }
        remove(party, member);
        return Result.OK;
    }

    public Result disband(UUID leader) {
        var party = byPlayer.get(leader);
        if (party == null) {
            return Result.NOT_IN_PARTY;
        }
        if (!party.leader.equals(leader)) {
            return Result.NOT_LEADER;
        }
        dissolve(party);
        return Result.OK;
    }

    /** Ends the party of whoever is a member of it, leader or not; false if they have none. */
    public boolean disbandOf(UUID member) {
        var party = byPlayer.get(member);
        if (party == null) {
            return false;
        }
        dissolve(party);
        return true;
    }

    private void remove(Live party, UUID player) {
        party.members.remove(player);
        byPlayer.remove(player);
        if (party.members.size() < 2) {
            dissolve(party);
        } else if (party.leader.equals(player)) {
            party.leader = party.members.getFirst();
        }
    }

    private void dissolve(Live party) {
        party.members.forEach(byPlayer::remove);
        byId.remove(party.id);
        invites.removeIf(invite -> invite.leader.equals(party.leader));
    }
}
