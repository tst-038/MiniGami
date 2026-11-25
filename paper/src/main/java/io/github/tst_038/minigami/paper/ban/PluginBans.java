package io.github.tst_038.minigami.paper.ban;

import io.github.tst_038.minigami.api.ban.Ban;
import io.github.tst_038.minigami.api.ban.MiniGamiBans;
import io.github.tst_038.minigami.api.ban.MiniGamiPlayerBannedEvent;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.storage.Storage;
import io.github.tst_038.minigami.core.time.Durations;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

/** MiniGami's {@link MiniGamiBans}: the bans in memory, written through to storage. */
public final class PluginBans implements MiniGamiBans, Listener {

    private static final Pattern PLUGIN_SCOPE = Pattern.compile("[a-z0-9_-]+");

    private final BanList list = new BanList();
    private final BanStore store;
    private final Supplier<? extends Collection<String>> games;
    private final Map<String, Plugin> registered = new ConcurrentHashMap<>();
    private final Server server;
    private final Messages messages;
    private final Executor serverThread;
    private final Clock clock;
    private final Logger logger;

    /** @param games the ids of the games loaded now */
    public PluginBans(Storage storage, Supplier<? extends Collection<String>> games, Server server, Messages messages, Executor serverThread,
            Clock clock, Logger logger) {
        this.store = new BanStore(storage);
        this.games = games;
        this.server = server;
        this.messages = messages;
        this.serverThread = serverThread;
        this.clock = clock;
        this.logger = logger;
    }

    /** Reads the bans from storage into memory, off the server thread; until then nobody counts as banned. */
    public void load() {
        store.load(clock.instant()).whenComplete((bans, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not load the bans", failure);
            } else {
                bans.forEach(list::put);
            }
        });
    }

    @Override
    public void register(Plugin plugin, String scope) {
        if (!PLUGIN_SCOPE.matcher(scope).matches() || scope.equals(ALL) || scope.equals(NIGHTS)) {
            throw new IllegalArgumentException("Not a scope a plugin can register: '" + scope + "'");
        }
        registered.put(scope, plugin);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        registered.values().removeIf(event.getPlugin()::equals);
    }

    @Override
    public Set<String> scopes() {
        var scopes = new TreeSet<String>(registered.keySet());
        games.get().forEach(game -> scopes.add(MiniGamiBans.game(game)));
        scopes.add(ALL);
        scopes.add(NIGHTS);
        return scopes;
    }

    @Override
    public Optional<Ban> ban(UUID player, String scope) {
        return find(player, List.of(scope));
    }

    /** The longest-lasting ban that keeps the player out of any of the scopes. */
    public Optional<Ban> find(UUID player, Collection<String> scopes) {
        return list.find(player, scopes, clock.instant());
    }

    @Override
    public Collection<Ban> bans() {
        return list.active(clock.instant());
    }

    /** The bans of the player that have not ended. */
    public List<Ban> of(UUID player) {
        return bans().stream().filter(ban -> ban.player().equals(player)).toList();
    }

    /** The player with a ban, ended or not, under this name, ignoring case. */
    public Optional<UUID> named(String name) {
        return list.named(name);
    }

    @Override
    public CompletableFuture<Ban> ban(UUID player, String name, String scope, Optional<Duration> length, Optional<String> reason, String by) {
        if (!scopes().contains(scope)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No ban scope '" + scope + "'"));
        }
        var now = clock.instant();
        var ban = new Ban(player, name, scope, length.map(now::plus), reason.filter(text -> !text.isBlank()), by, now);
        return store.save(ban).thenApplyAsync(saved -> {
            list.put(ban);
            var online = Optional.ofNullable(server.getPlayer(player));
            online.ifPresent(banned -> messages.send(banned, "ban.you-were-banned", tags(ban)));
            server.getPluginManager().callEvent(new MiniGamiPlayerBannedEvent(online, ban));
            return ban;
        }, serverThread);
    }

    @Override
    public CompletableFuture<List<Ban>> unban(UUID player, Optional<String> scope) {
        return store.delete(player, scope).thenApplyAsync(deleted -> {
            var now = clock.instant();
            return list.remove(player, scope).stream().filter(ban -> !ban.isOver(now)).toList();
        }, serverThread);
    }

    /**
     * Whether a ban keeps the player out of any of the scopes; if so, {@code feedback} hears it: as their own ban when it is the
     * player, else as the player's, as for a party leader or staff.
     */
    public boolean keepsOut(Audience feedback, Player player, Collection<String> scopes) {
        var ban = find(player.getUniqueId(), scopes);
        ban.ifPresent(found -> messages.send(feedback, feedback == player ? "ban.you-are-banned" : "ban.player-is-banned", tags(found)));
        return ban.isPresent();
    }

    /** {@code %player%}, {@code %scope%}, {@code %left%} ("for 2h 5m" or "for good"), {@code %reason%} and {@code %by%}. */
    public TagResolver tags(Ban ban) {
        return TagResolver.resolver(Placeholder.unparsed("player", ban.name()), Placeholder.unparsed("scope", ban.scope()),
                Placeholder.component("left", length(ban.left(clock.instant()))), Placeholder.component("reason",
                        ban.reason().<Component>map(Component::text).orElseGet(() -> messages.render("ban.no-reason"))),
                Placeholder.unparsed("by", ban.by()));
    }

    /** "for 2h 5m", or "for good" for a ban that never ends. */
    public Component length(Optional<Duration> length) {
        return length.map(time -> messages.render("ban.left", Placeholder.unparsed("time", Durations.format(time))))
                .orElseGet(() -> messages.render("ban.forever"));
    }
}
