package io.github.tst_038.minigami.paper.schedule;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.schedule.Rotation;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.Variant;
import io.github.tst_038.minigami.paper.match.LiveMatch;
import io.github.tst_038.minigami.paper.match.PublicMatches;
import io.github.tst_038.minigami.paper.match.RunningMatches;
import io.github.tst_038.minigami.paper.night.Nights;
import io.github.tst_038.minigami.paper.click.Clicks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Plays the schedules. At each of a schedule's times it opens a public match of its next game that
 * the players who could join are enough for, and calls them to it with a click, once more halfway.
 * Whoever answers the call joins without the game's play permission; that is what calls are for.
 * If the match hasn't started when the call closes, it is stopped. Once it has, its players may win
 * the schedule's rewards.
 *
 * <p>A schedule skips its time during a game night, while its last call is still open, and while it
 * is paused. When no game fits, players see nothing; hosts hear about it.
 *
 * <p>Not thread-safe: use it from the server thread only, ticking it once a second.
 */
public final class Schedules {

    public static final String JOIN_PERMISSION = "minigami.schedule.join";

    private static final DateTimeFormatter NEXT_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);

    /** Why a schedule did or didn't open a game when its time came. */
    private enum Outcome {
        OPENED,
        NIGHT,
        CALL_OPEN,
        NOTHING_FITS,
        OPEN_FAILED
    }

    /** Where a schedule stands: its rotation and next time, made anew when a reload changes the schedule. */
    private static final class Turn {
        final Schedule schedule;
        final Rotation<Schedule.Entry> rotation;
        Optional<LocalDateTime> next;

        Turn(Schedule schedule, Rotation<Schedule.Entry> rotation, Optional<LocalDateTime> next) {
            this.schedule = schedule;
            this.rotation = rotation;
            this.next = next;
        }
    }

    /**
     * A schedule's game open to join. Its match is let go when everyone in it leaves before it
     * starts, and opened again by whoever answers the call next, so the call lasts its full time.
     */
    private static final class Call {
        final Schedule schedule;
        final Variant variant;
        LiveMatch match;
        final Instant remindAt;
        final Instant closesAt;
        boolean reminded;

        Call(Schedule schedule, LiveMatch match, Instant opened) {
            this.schedule = schedule;
            this.variant = match.variant();
            this.match = match;
            this.remindAt = opened.plusSeconds(schedule.joinSeconds() / 2);
            this.closesAt = opened.plusSeconds(schedule.joinSeconds());
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Supplier<IdRegistry<Schedule>> schedules;
    private final Supplier<IdRegistry<Game>> games;
    private final PublicMatches publicMatches;
    private final RunningMatches running;
    private final BooleanSupplier nightRunning;
    private final ScheduleRewards rewards;
    private final Clock clock;
    private final RandomGenerator random;
    private final Map<String, Turn> turns = new HashMap<>();
    // shortcut: pauses are kept in memory, so a restart resumes every schedule; store them if staff need a pause to last.
    private final Set<String> paused = new HashSet<>();
    /** Oldest first. */
    private final List<Call> calls = new ArrayList<>();

    /**
     * @param schedules the schedules currently loaded; config loading replaces the registry
     * @param games the games currently loaded
     * @param nightRunning whether a game night is on
     * @param clock tells the time schedules are written in, the server's
     */
    public Schedules(Plugin plugin, Messages messages, Supplier<IdRegistry<Schedule>> schedules, Supplier<IdRegistry<Game>> games, PublicMatches publicMatches,
            RunningMatches running, BooleanSupplier nightRunning, ScheduleRewards rewards, Clock clock, RandomGenerator random) {
        this.plugin = plugin;
        this.messages = messages;
        this.schedules = schedules;
        this.games = games;
        this.publicMatches = publicMatches;
        this.running = running;
        this.nightRunning = nightRunning;
        this.rewards = rewards;
        this.clock = clock;
        this.random = random;
    }

    public Collection<String> ids() {
        return schedules.get().ids();
    }

    /** Opens what is due and runs the open calls; once a second. */
    public void tick() {
        var now = LocalDateTime.now(clock);
        var loaded = schedules.get();
        turns.keySet().retainAll(loaded.ids());
        for (var id : loaded.ids()) {
            var turn = turn(loaded.find(id).orElseThrow(), now);
            if (turn.next.isPresent() && !now.isBefore(turn.next.get())) {
                turn.next = turn.schedule.when().next(now);
                if (!paused.contains(id) && fire(turn) == Outcome.NOTHING_FITS) {
                    messages.send(hosts(), "schedule.skipped", Placeholder.parsed("name", turn.schedule.name()), Formatter.number("players", couldJoin().size()));
                }
            }
        }
        tickCalls();
        rewards.tick();
    }

    private Turn turn(Schedule schedule, LocalDateTime now) {
        var turn = turns.get(schedule.id());
        if (turn == null || turn.schedule != schedule) {
            turn = new Turn(schedule, new Rotation<>(schedule.games(), schedule.pick() == Schedule.Pick.RANDOM, random), schedule.when().next(now));
            turns.put(schedule.id(), turn);
        }
        return turn;
    }

    private Outcome fire(Turn turn) {
        if (nightRunning.getAsBoolean()) {
            return Outcome.NIGHT;
        }
        if (calls.stream().anyMatch(call -> call.schedule.id().equals(turn.schedule.id()))) {
            return Outcome.CALL_OPEN;
        }
        int players = couldJoin().size();
        if (players < turn.schedule.minOnline()) {
            return Outcome.NOTHING_FITS;
        }
        var picked = turn.rotation.next(entry -> variant(entry).filter(variant -> fits(variant, players)).isPresent()).flatMap(this::variant);
        if (picked.isEmpty()) {
            return Outcome.NOTHING_FITS;
        }
        Optional<LiveMatch> match;
        try {
            match = publicMatches.open(picked.get());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Schedule " + turn.schedule.id() + " could not open " + picked.get().key(), e);
            match = Optional.empty();
        }
        if (match.isEmpty()) {
            return Outcome.OPEN_FAILED;
        }
        var call = new Call(turn.schedule, match.get(), clock.instant());
        calls.add(call);
        announce(call, "schedule.call");
        return Outcome.OPENED;
    }

    private Optional<Variant> variant(Schedule.Entry entry) {
        return games.get().find(entry.game()).flatMap(game -> game.variant(entry.variant()));
    }

    /** Enough players could join, and one of its arenas is free. */
    private boolean fits(Variant variant, int players) {
        return variant.minPlayers() <= players && variant.arenas().stream().anyMatch(arena -> !running.isArenaInUse(arena.id()));
    }

    /** Online, allowed to answer calls, and not playing. */
    private List<Player> couldJoin() {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(JOIN_PERMISSION) && running.find(player.getUniqueId()).isEmpty())
                .map(Player.class::cast).toList();
    }

    private void announce(Call call, String key) {
        long seconds = Math.max(0, (Duration.between(clock.instant(), call.closesAt).toMillis() + 999) / 1000);
        messages.send(Audience.audience(couldJoin()), key, Placeholder.parsed("name", call.schedule.name()), Placeholder.parsed("game", call.variant.name()),
                Formatter.number("seconds", seconds), Formatter.number("needed", call.variant.minPlayers()),
                Placeholder.styling("join", Clicks.by(player -> {
                    if (player.hasPermission(JOIN_PERMISSION)) {
                        join(player, Optional.empty());
                    }
                })));
    }

    private void tickCalls() {
        var now = clock.instant();
        for (var iterator = calls.iterator(); iterator.hasNext(); ) {
            var call = iterator.next();
            if (call.match != null && call.match.match().isFinished()) {
                // Ended before it started, so its players all left or staff stopped it.
                call.match = null;
            }
            if (call.match != null && !call.match.isJoinable()) {
                iterator.remove();
                rewards.watch(call.schedule, call.match);
            } else if (!now.isBefore(call.closesAt)) {
                iterator.remove();
                // A match whose start is counting down is let to start; its players are enough.
                if (call.match != null && call.match.startsIn().isEmpty()) {
                    var game = Placeholder.parsed("game", call.match.variant().name());
                    if (call.match.members().isEmpty()) {
                        messages.send(Audience.audience(couldJoin()), "schedule.nobody-joined", Placeholder.parsed("name", call.schedule.name()), game);
                    } else {
                        messages.send(call.match.audience(), "schedule.not-enough-players", game);
                    }
                    call.match.stop();
                } else if (call.match != null) {
                    rewards.watch(call.schedule, call.match);
                } else {
                    // Its match ended before the call closed because everyone who joined left again.
                    messages.send(Audience.audience(couldJoin()), "schedule.nobody-joined", Placeholder.parsed("name", call.schedule.name()),
                            Placeholder.parsed("game", call.variant.name()));
                }
            } else if (!call.reminded && !now.isBefore(call.remindAt)) {
                call.reminded = true;
                announce(call, "schedule.reminder");
            }
        }
    }

    /**
     * {@code /mg schedule join [schedule]}: joins the newest call that is open (of that schedule, when one is named),
     * opening its match again if everyone had left it.
     */
    public void join(Player player, Optional<String> id) {
        join(player, player, id);
    }

    /** An admin puts another player in the call; the admin hears why if that can't be done. */
    public void join(CommandSender sender, Player player, Optional<String> id) {
        if (id.isPresent() && find(sender, id.get()).isEmpty()) {
            return;
        }
        var open = calls.reversed().stream().filter(call -> id.isEmpty() || call.schedule.id().equals(id.get()))
                .filter(call -> call.match == null || call.match.match().isFinished() || call.match.isJoinable()).findFirst();
        if (open.isEmpty()) {
            messages.send(sender, "schedule.no-call");
            return;
        }
        var call = open.get();
        if (call.match == null || call.match.match().isFinished()) {
            var match = publicMatches.open(call.variant);
            if (match.isEmpty()) {
                messages.send(sender, "schedule.no-arena", Placeholder.parsed("game", call.variant.name()));
                return;
            }
            call.match = match.get();
        }
        publicMatches.join(player, call.match);
    }

    public void list(CommandSender sender) {
        var loaded = schedules.get();
        if (loaded.ids().isEmpty()) {
            messages.send(sender, "schedule.none");
            return;
        }
        var now = LocalDateTime.now(clock);
        messages.send(sender, "schedule.list-header");
        for (var id : loaded.ids()) {
            var turn = turn(loaded.find(id).orElseThrow(), now);
            var state = paused.contains(id) ? messages.render("schedule.state.paused")
                    : calls.stream().anyMatch(call -> call.schedule.id().equals(id)) ? messages.render("schedule.state.calling") : Component.empty();
            messages.send(sender, "schedule.list-entry", Placeholder.unparsed("id", id), Placeholder.parsed("name", turn.schedule.name()),
                    Placeholder.unparsed("next", turn.next.map(NEXT_TIME::format).orElse("never")), Placeholder.component("state", state));
        }
    }

    /** Opens the schedule's next game now, paused or not; its times stay as they are. */
    public void now(CommandSender sender, String id) {
        var schedule = find(sender, id);
        if (schedule.isEmpty()) {
            return;
        }
        var name = names(schedule.get());
        switch (fire(turn(schedule.get(), LocalDateTime.now(clock)))) {
            case OPENED -> messages.send(sender, "schedule.opened", name);
            case NIGHT -> messages.send(sender, "schedule.night-running", name);
            case CALL_OPEN -> messages.send(sender, "schedule.call-open", name);
            case NOTHING_FITS -> messages.send(sender, "schedule.skipped", name, Formatter.number("players", couldJoin().size()));
            case OPEN_FAILED -> messages.send(sender, "schedule.open-failed", name);
        }
    }

    public void pause(CommandSender sender, String id) {
        find(sender, id).ifPresent(schedule -> messages.send(sender, paused.add(id) ? "schedule.paused" : "schedule.already-paused", names(schedule)));
    }

    public void resume(CommandSender sender, String id) {
        find(sender, id).ifPresent(schedule -> messages.send(sender, paused.remove(id) ? "schedule.resumed" : "schedule.not-paused", names(schedule)));
    }

    private Optional<Schedule> find(CommandSender sender, String id) {
        var schedule = schedules.get().find(id);
        if (schedule.isEmpty()) {
            messages.send(sender, "schedule.unknown", Placeholder.unparsed("schedule", id), Placeholder.unparsed("schedules", String.join(", ", ids())));
        }
        return schedule;
    }

    private static TagResolver names(Schedule schedule) {
        return Placeholder.parsed("name", schedule.name());
    }

    private Audience hosts() {
        return plugin.getServer().filterAudience(audience -> audience instanceof CommandSender sender && sender.hasPermission(Nights.HOST_PERMISSION)
                && !(sender instanceof Player player && running.find(player.getUniqueId()).isPresent()));
    }
}
