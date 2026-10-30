package io.github.tst_038.minigami.thewall;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.core.roster.Participant;
import org.bukkit.entity.Player;

/**
 * A border around the centre of the arena's {@code border} region that closes in from the region's
 * widest side; players outside it are hurt each second. Only the match's players see it, so the world's
 * own border stays as it is; they get the world's back when the match gives them back their things.
 */
final class ClosingBorder implements Feature {

    static final String REGION = "border";

    /**
     * @param endSize the border's width in blocks once it stops closing
     * @param shrinkTicks how long it takes to close
     * @param damage health taken each second outside it, in half hearts
     */
    record Settings(int endSize, long shrinkTicks, double damage) {

        static Settings read(ConfigReader config) {
            return new Settings(config.has("end-size") ? config.integer("end-size", 1, 10000) : 20,
                    (config.has("shrink-seconds") ? config.integer("shrink-seconds", 1, 7200) : 300) * 20L,
                    config.has("damage") ? config.integer("damage", 0, 40) : 1);
        }
    }

    private final Settings settings;

    ClosingBorder(Settings settings) {
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
            var half = BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), context.clock().elapsedTicks() - begin) / 2;
            for (Player player : context.players()) {
                var at = player.getLocation();
                var alive = context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent();
                if (alive && (Math.abs(at.getX() - centerX) > half || Math.abs(at.getZ() - centerZ) > half)) {
                    player.damage(settings.damage());
                }
            }
        }));
        context.scope().register(() -> context.players().forEach(player -> player.setWorldBorder(null)));
    }

    // shortcut: a paused match clock doesn't pause the border players see, only the damage; send it again on resume if pausing The Wall becomes common.
    private void show(Player player, double centerX, double centerZ, double start, long elapsed) {
        var border = player.getServer().createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), elapsed));
        var left = settings.shrinkTicks() - elapsed;
        if (left > 0) {
            border.changeSize(settings.endSize(), left);
        }
        player.setWorldBorder(border);
    }
}
