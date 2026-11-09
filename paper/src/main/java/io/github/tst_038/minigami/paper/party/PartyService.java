package io.github.tst_038.minigami.paper.party;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.party.Parties;
import io.github.tst_038.minigami.core.party.Parties.Party;
import io.github.tst_038.minigami.core.party.Parties.Result;
import io.github.tst_038.minigami.paper.storage.Storage;
import io.github.tst_038.minigami.paper.click.Clicks;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Parties: groups of players that stay together between matches and across restarts. Tells the
 * members what happens and saves each change in the background. Call it from the server thread.
 * Game nights ask it who is in which party.
 */
public final class PartyService {

    /** How long an invite can be accepted. */
    private static final long INVITE_TICKS = 60 * 20;

    private record Invite(UUID leader, UUID invitee) {
    }

    private final Plugin plugin;
    private final PartyStore store;
    private final Messages messages;
    private final Parties parties;
    /** The timer that ends each open invite. */
    private final Map<Invite, ScheduledTask> invites = new HashMap<>();

    public PartyService(Plugin plugin, Storage storage, Messages messages) {
        this.plugin = plugin;
        this.store = new PartyStore(storage, plugin.getLogger());
        this.messages = messages;
        this.parties = new Parties();
    }

    /** Brings the saved parties back; they are there a moment after startup. */
    public void load() {
        store.loadAll().whenCompleteAsync((saved, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load the parties", failure);
                return;
            }
            saved.forEach(party -> parties.restore(party.id(), party.leader(), party.members()));
        }, task -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, task));
    }

    /** The members of the player's party, in the order they joined; empty if they have none. */
    public Optional<Set<UUID>> partyOf(UUID player) {
        return parties.of(player).map(PartyService::members);
    }

    /** Whether the player leads the party they are in; false if they have none. */
    public boolean isLeader(UUID player) {
        return parties.of(player).filter(party -> party.leader().equals(player)).isPresent();
    }

    /** Every party's members. */
    public List<Set<UUID>> all() {
        return parties.all().stream().map(PartyService::members).toList();
    }

    /** Ends the party the player is in, whoever leads it, and tells its members; false if they have none. */
    public boolean disband(UUID member) {
        var party = parties.of(member);
        if (party.isEmpty() || !parties.disbandOf(member)) {
            return false;
        }
        ended(party.get());
        return true;
    }

    /** Names of the players who invited this one and whose invite is still open. */
    public Collection<String> inviters(UUID invitee) {
        return invites.keySet().stream().filter(invite -> invite.invitee().equals(invitee)).map(invite -> name(invite.leader())).toList();
    }

    /** Names of the other members of the player's party. */
    public Collection<String> otherMembers(UUID player) {
        return parties.of(player).map(party -> party.members().stream().filter(member -> !member.equals(player)).map(this::name).toList()).orElse(List.of());
    }

    public void invite(Player leader, Player target) {
        var had = parties.of(leader.getUniqueId()).isPresent();
        var result = parties.invite(leader.getUniqueId(), target.getUniqueId());
        if (result != Result.OK) {
            refuse(leader, result, target.getName());
            return;
        }
        if (!had) {
            store.save(parties.of(leader.getUniqueId()).orElseThrow());
        }
        var invite = new Invite(leader.getUniqueId(), target.getUniqueId());
        var old = invites.put(invite, plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            invites.remove(invite);
            if (parties.expire(invite.leader(), invite.invitee())) {
                tell(List.of(invite.leader(), invite.invitee()), "party.invite-expired", Placeholder.unparsed("leader", leader.getName()),
                        Placeholder.unparsed("player", target.getName()));
            }
        }, INVITE_TICKS));
        if (old != null) {
            old.cancel();
        }
        messages.send(leader, "party.invited", Placeholder.unparsed("player", target.getName()));
        messages.send(target, "party.invite", Placeholder.unparsed("leader", leader.getName()),
                Placeholder.styling("accept", Clicks.by(invitee -> accept(invitee, leader.getName()))));
    }

    /** @param leaderName who invited the player, as typed */
    public void accept(Player player, String leaderName) {
        var leader = plugin.getServer().getPlayerExact(leaderName);
        var result = leader == null ? Result.NO_INVITE : parties.accept(player.getUniqueId(), leader.getUniqueId());
        if (result == Result.IN_PARTY) {
            messages.send(player, "party.leave-first");
            return;
        }
        if (result != Result.OK) {
            refuse(player, result, leaderName);
            return;
        }
        var invite = invites.remove(new Invite(leader.getUniqueId(), player.getUniqueId()));
        if (invite != null) {
            invite.cancel();
        }
        var party = parties.of(player.getUniqueId()).orElseThrow();
        store.save(party);
        messages.send(player, "party.joined", Placeholder.unparsed("leader", leaderName));
        tell(others(party, player.getUniqueId()), "party.member-joined", Placeholder.unparsed("player", player.getName()));
    }

    public void leave(Player player) {
        var id = player.getUniqueId();
        var before = parties.of(id);
        if (parties.leave(id) != Result.OK) {
            messages.send(player, "party.none");
            return;
        }
        messages.send(player, "party.left");
        removed(before.orElseThrow(), id, "party.member-left");
    }

    /** @param name a member of the party, as typed; also one who is offline */
    public void kick(Player leader, String name) {
        var party = parties.of(leader.getUniqueId());
        if (party.isEmpty()) {
            messages.send(leader, "party.none");
            return;
        }
        var target = party.get().members().stream().filter(member -> name(member).equalsIgnoreCase(name)).findFirst();
        if (target.isEmpty()) {
            messages.send(leader, "party.not-member", Placeholder.unparsed("player", name));
            return;
        }
        var result = parties.kick(leader.getUniqueId(), target.get());
        if (result != Result.OK) {
            refuse(leader, result, name);
            return;
        }
        tell(List.of(target.get()), "party.kicked", Placeholder.unparsed("leader", leader.getName()));
        removed(party.get(), target.get(), "party.member-kicked");
    }

    public void disband(Player leader) {
        var party = parties.of(leader.getUniqueId());
        var result = parties.disband(leader.getUniqueId());
        if (result != Result.OK) {
            refuse(leader, result, leader.getName());
            return;
        }
        ended(party.orElseThrow());
    }

    public void list(Player player) {
        var party = parties.of(player.getUniqueId());
        if (party.isEmpty()) {
            messages.send(player, "party.none");
            return;
        }
        messages.send(player, "party.list-header", Formatter.number("count", party.get().members().size()));
        for (var member : party.get().members()) {
            var online = plugin.getServer().getPlayer(member) != null;
            messages.send(player, member.equals(party.get().leader()) ? "party.list-leader" : "party.list-member",
                    Placeholder.unparsed("player", name(member)),
                    Placeholder.component("status", messages.render(online ? "party.online" : "party.offline")));
        }
    }

    private static Set<UUID> members(Party party) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(party.members()));
    }

    private void refuse(Player player, Result result, String name) {
        var key = switch (result) {
            case NOT_IN_PARTY -> "party.none";
            case NOT_LEADER -> "party.not-leader";
            case IN_PARTY -> "party.invite-in-party";
            case NO_INVITE -> "party.no-invite";
            case SELF -> "party.self";
            case OK -> throw new IllegalArgumentException("Nothing to refuse");
        };
        messages.send(player, key, Placeholder.unparsed("player", name), Placeholder.unparsed("leader", name));
    }

    /** Someone is out of the party: tells the rest, hands over a lost leadership, and saves what is left. */
    private void removed(Party before, UUID gone, String key) {
        var rest = before.members().stream().filter(member -> !member.equals(gone)).toList();
        var after = rest.stream().findFirst().flatMap(parties::of);
        if (after.isEmpty()) {
            tell(rest, "party.disbanded");
            store.delete(before.id());
            return;
        }
        tell(rest, key, Placeholder.unparsed("player", name(gone)));
        if (!after.get().leader().equals(before.leader())) {
            tell(rest, "party.new-leader", Placeholder.unparsed("player", name(after.get().leader())));
        }
        store.save(after.get());
    }

    private void ended(Party party) {
        tell(party.members(), "party.disbanded");
        store.delete(party.id());
    }

    private static List<UUID> others(Party party, UUID player) {
        return party.members().stream().filter(member -> !member.equals(player)).toList();
    }

    private void tell(Collection<UUID> players, String key, TagResolver... tags) {
        for (var id : players) {
            var online = plugin.getServer().getPlayer(id);
            if (online != null) {
                messages.send(online, key, tags);
            }
        }
    }

    private String name(UUID id) {
        var name = plugin.getServer().getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }
}
