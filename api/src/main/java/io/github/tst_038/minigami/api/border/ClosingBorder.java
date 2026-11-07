package io.github.tst_038.minigami.api.border;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.core.border.BorderSize;
import io.github.tst_038.minigami.core.roster.Participant;
import org.bukkit.entity.Player;

/**
 * A border around the centre of the arena's {@code border} region that closes in from the region's
 * widest side; players outside it are hurt each second. Only the match's players see it, so the world's
 * own border stays as it is; they get the world's back when the match gives them back their things.
 * It closes evenly from every side towards the centre.
 *
 * <p>Players are only ever told where it will be one second later, so it stands still as soon as the
 * match clock does, while the match is paused.
 */
public final class ClosingBorder implements Feature {

    public static final String REGION = "border";

    /**
     * @param endSize the border's width in blocks once it stops closing
     * @param shrinkTicks how long it takes to close
     * @param damage health taken each second outside it, in half hearts
     */
    public record Settings(int endSize, long shrinkTicks, double damage) {

        public static Settings read(ConfigReader config) {
            return new Settings(config.has("end-size") ? config.integer("end-size", 1, 10000) : 20,
                    (config.has("shrink-seconds") ? config.integer("shrink-seconds", 1, 7200) : 300) * 20L,
                    config.has("damage") ? config.integer("damage", 0, 40) : 1);
        }
    }

    private final Settings settings;

    public ClosingBorder(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        var region = context.region(REGION).orElseThrow();
        double centerX = (region.minX() + region.maxX() + 1) / 2.0;
        double centerZ = (region.minZ() + region.maxZ() + 1) / 2.0;
        double start = Math.max(region.maxX() - region.minX() + 1, region.maxZ() - region.minZ() + 1);
        long begin = context.clock().elapsedTicks();
        Runnable showAll = () -> context.players().forEach(player -> show(player, centerX, centerZ, start, context.clock().elapsedTicks() - begin));
        showAll.run();
        // A respawn makes a new player entity, and one coming back had the world's border; both need the match's again.
        context.on(PlayerPostRespawnEvent.class, event -> show(event.getPlayer(), centerX, centerZ, start, context.clock().elapsedTicks() - begin));
        context.on(MatchRejoinEvent.class, event -> show(event.getPlayer(), centerX, centerZ, start, context.clock().elapsedTicks() - begin));
        context.scope().register(context.clock().repeat(20, () -> {
            var elapsed = context.clock().elapsedTicks() - begin;
            var half = BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), elapsed) / 2;
            var next = BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), elapsed + 20);
            for (Player player : context.players()) {
                var border = player.getWorldBorder();
                if (border == null) {
                    show(player, centerX, centerZ, start, elapsed);
                } else {
                    border.changeSize(next, 20);
                }
                var at = player.getLocation();
                var alive = context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent();
                if (alive && (Math.abs(at.getX() - centerX) > half || Math.abs(at.getZ() - centerZ) > half)) {
                    player.damage(settings.damage());
                }
            }
        }));
        context.scope().register(() -> context.players().forEach(player -> player.setWorldBorder(null)));
    }

    private void show(Player player, double centerX, double centerZ, double start, long elapsed) {
        var border = player.getServer().createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), elapsed));
        player.setWorldBorder(border);
    }
}
